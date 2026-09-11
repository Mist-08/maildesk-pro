package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.SystemMailService;
import com.mycompany.maildesk.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emisión y verificación de códigos de verificación en dos pasos.
 *
 * <ul>
 *   <li>Código de ocho dígitos con SecureRandom, vigencia configurable (5 min), un solo uso.</li>
 *   <li>Máximo de intentos por desafío; reenvío tras un intervalo mínimo; máximo de solicitudes por
 *       hora por cuenta y por IP (acumulativos, no se reinician al reenviar).</li>
 *   <li>Solo se persiste el HMAC del código; la verificación incrementa intentos y consume de forma
 *       atómica para evitar reutilización y carreras.</li>
 * </ul>
 */
@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);

    public enum Outcome { SUCCESS, INVALID_CODE, EXPIRED_OR_USED, TOO_MANY_ATTEMPTS, SESSION_MISMATCH }

    public record Verification(Outcome outcome, int remainingAttempts, String payload) {
        public boolean success() { return outcome == Outcome.SUCCESS; }
    }

    private final AuthChallengeRepository challenges;
    private final RateLimitService rateLimits;
    private final OtpCodeGenerator codeGenerator;
    private final OtpHmacKey hmacKey;
    private final SystemMailService systemMail;
    private final AppProperties properties;
    private final TransactionTemplate tx;

    public ChallengeService(AuthChallengeRepository challenges, RateLimitService rateLimits,
                            OtpCodeGenerator codeGenerator, OtpHmacKey hmacKey, SystemMailService systemMail,
                            AppProperties properties, PlatformTransactionManager transactionManager) {
        this.challenges = challenges;
        this.rateLimits = rateLimits;
        this.codeGenerator = codeGenerator;
        this.hmacKey = hmacKey;
        this.systemMail = systemMail;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Crea un nuevo desafío para el usuario, invalida los anteriores del mismo propósito y envía el
     * código al correo indicado. Lanza {@link BusinessException} si se superan los límites.
     */
    public PendingChallenge issue(User user, ChallengePurpose purpose, String targetEmail, String payload,
                                  String ip, String purposeLabel) {
        AppProperties.Security sec = properties.getSecurity();
        Instant now = Instant.now();

        Optional<AuthChallenge> last = challenges.findFirstByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose);
        // El intervalo mínimo aplica mientras el desafío anterior siga vigente (reenvío); el límite por
        // hora aplica siempre.
        if (last.isPresent() && last.get().isActive(now)) {
            long elapsed = Duration.between(last.get().getCreatedAt(), now).getSeconds();
            if (elapsed < sec.getOtpResendIntervalSeconds()) {
                throw new BusinessException("Espera " + (sec.getOtpResendIntervalSeconds() - elapsed)
                        + " segundos antes de solicitar otro código.");
            }
        }
        String userBucket = "otp:user:" + user.getId() + ":" + purpose;
        String ipBucket = "otp:ip:" + ip;
        if (rateLimits.isLimited(userBucket, sec.getOtpMaxRequestsPerHour(), Duration.ofHours(1))) {
            throw new BusinessException("Se alcanzó el máximo de códigos por hora para esta cuenta. Intenta más tarde.");
        }
        if (rateLimits.isLimited(ipBucket, sec.getOtpMaxRequestsPerIpPerHour(), Duration.ofHours(1))) {
            throw new BusinessException("Demasiadas solicitudes desde esta conexión. Intenta más tarde.");
        }
        rateLimits.record(userBucket);
        rateLimits.record(ipBucket);

        String code = codeGenerator.generate();
        String bindingToken = Hashing.randomToken();

        AuthChallenge challenge = tx.execute(status -> {
            challenges.invalidateActive(user.getId(), purpose, now);
            AuthChallenge c = new AuthChallenge();
            c.setUserId(user.getId());
            c.setPurpose(purpose);
            c.setCodeHmac("pending");
            c.setSessionBindingHash(Hashing.sha256Hex(bindingToken));
            c.setPayload(payload);
            c.setMaxAttempts(sec.getOtpMaxAttempts());
            c.setExpiresAt(now.plusSeconds(sec.getOtpTtlSeconds()));
            c.setRequestIp(ip);
            c = challenges.saveAndFlush(c);
            c.setCodeHmac(hmacKey.hmac(c.getId(), code));
            return challenges.saveAndFlush(c);
        });

        try {
            systemMail.sendVerificationCode(targetEmail, code, purposeLabel, sec.getOtpTtlSeconds() / 60);
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> challenges.invalidate(challenge.getId(), Instant.now()));
            throw e;
        }
        log.info("Desafío {} emitido para usuario {} ({})", challenge.getId(), user.getId(), purpose);
        return new PendingChallenge(user.getId(), challenge.getId(), purpose, bindingToken,
                PendingChallenge.mask(targetEmail), now);
    }

    /** Verifica y consume el desafío de forma atómica. */
    @Transactional
    public Verification verify(PendingChallenge pending, String submittedCode) {
        Instant now = Instant.now();
        String code = submittedCode == null ? "" : submittedCode.replaceAll("\\s", "");

        int admitted = challenges.registerAttempt(pending.challengeId(), now);
        Optional<AuthChallenge> loaded = challenges.findById(pending.challengeId());
        if (loaded.isEmpty()) {
            return new Verification(Outcome.EXPIRED_OR_USED, 0, null);
        }
        AuthChallenge challenge = loaded.get();
        if (admitted == 0) {
            if (challenge.getConsumedAt() == null && challenge.getInvalidatedAt() == null
                    && challenge.getAttempts() >= challenge.getMaxAttempts()) {
                return new Verification(Outcome.TOO_MANY_ATTEMPTS, 0, null);
            }
            return new Verification(Outcome.EXPIRED_OR_USED, 0, null);
        }
        if (!challenge.getUserId().equals(pending.userId()) || challenge.getPurpose() != pending.purpose()
                || !Hashing.constantTimeEquals(challenge.getSessionBindingHash(), Hashing.sha256Hex(pending.bindingToken()))) {
            return new Verification(Outcome.SESSION_MISMATCH, 0, null);
        }
        int remaining = Math.max(0, challenge.getMaxAttempts() - challenge.getAttempts());
        if (!code.matches("\\d{" + OtpCodeGenerator.LENGTH + "}")
                || !Hashing.constantTimeEquals(challenge.getCodeHmac(), hmacKey.hmac(challenge.getId(), code))) {
            return new Verification(remaining == 0 ? Outcome.TOO_MANY_ATTEMPTS : Outcome.INVALID_CODE, remaining, null);
        }
        int consumed = challenges.consume(challenge.getId(), now);
        if (consumed != 1) {
            return new Verification(Outcome.EXPIRED_OR_USED, 0, null);
        }
        return new Verification(Outcome.SUCCESS, remaining, challenge.getPayload());
    }

    @Transactional
    public void cancel(PendingChallenge pending) {
        if (pending != null) {
            challenges.invalidate(pending.challengeId(), Instant.now());
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    @Transactional
    public void purgeExpired() {
        challenges.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }
}
