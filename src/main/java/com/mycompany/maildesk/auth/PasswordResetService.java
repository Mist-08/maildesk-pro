package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.SystemMailService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserService;
import com.mycompany.maildesk.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recuperación de contraseña con token independiente (256 bits aleatorios, solo se guarda su hash),
 * temporal y de un solo uso. Al completarse se revocan todas las sesiones del usuario y el siguiente
 * acceso vuelve a exigir la verificación en dos pasos.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final UserService userService;
    private final SystemMailService systemMail;
    private final RateLimitService rateLimits;
    private final AuditService audit;
    private final AppProperties properties;
    private final TransactionTemplate tx;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users, UserService userService,
                                SystemMailService systemMail, RateLimitService rateLimits, AuditService audit,
                                AppProperties properties, PlatformTransactionManager transactionManager) {
        this.tokens = tokens;
        this.users = users;
        this.userService = userService;
        this.systemMail = systemMail;
        this.rateLimits = rateLimits;
        this.audit = audit;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Siempre responde igual, exista o no la cuenta, para no revelar direcciones registradas. */
    public void request(String rawEmail, String ip) {
        AppProperties.Security sec = properties.getSecurity();
        if (rateLimits.checkAndRecord("reset:ip:" + ip, sec.getResetMaxRequestsPerIpPerHour(), Duration.ofHours(1))) {
            throw new BusinessException("Demasiadas solicitudes. Intenta más tarde.");
        }
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase();
        Optional<User> found = users.findByEmailIgnoreCase(email).filter(u -> u.getStatus() == UserStatus.ACTIVE);
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (rateLimits.checkAndRecord("reset:user:" + user.getId(), 3, Duration.ofHours(1))) {
            return;
        }
        String token = Hashing.randomToken();
        Instant now = Instant.now();
        PasswordResetToken saved = tx.execute(status -> {
            tokens.invalidateAllFor(user.getId(), now);
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(user.getId());
            t.setTokenHash(Hashing.sha256Hex(token));
            t.setExpiresAt(now.plus(Duration.ofMinutes(sec.getResetTokenTtlMinutes())));
            t.setRequestIp(ip);
            return tokens.save(t);
        });
        String link = properties.getBaseUrl().replaceAll("/+$", "") + "/password/reset?token=" + token;
        try {
            systemMail.sendPasswordReset(user.getEmail(), link, sec.getResetTokenTtlMinutes());
            audit.recordAs(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER",
                    String.valueOf(user.getId()), null, ip);
        } catch (BusinessException e) {
            tx.executeWithoutResult(status -> tokens.consume(saved.getId(), Instant.now()));
            log.warn("No se pudo enviar el correo de recuperación para el usuario {}", user.getId());
        }
    }

    @Transactional(readOnly = true)
    public boolean isTokenUsable(String token) {
        return findUsable(token).isPresent();
    }

    private Optional<PasswordResetToken> findUsable(String token) {
        if (token == null || token.length() < 20 || token.length() > 200) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return tokens.findByTokenHash(Hashing.sha256Hex(token))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now));
    }

    @Transactional
    public void reset(String token, String password, String confirm, String ip) {
        PasswordResetToken resetToken = findUsable(token)
                .orElseThrow(() -> new BusinessException("El enlace no es válido, venció o ya fue utilizado."));
        User user = users.findById(resetToken.getUserId())
                .orElseThrow(() -> new BusinessException("El enlace no es válido."));
        PasswordPolicy.validate(password, confirm, user.getEmail());
        if (tokens.consume(resetToken.getId(), Instant.now()) != 1) {
            throw new BusinessException("El enlace ya fue utilizado.");
        }
        userService.updatePasswordAndRevokeSessions(user.getId(), password);
        audit.recordAs(user.getId(), user.getEmail(), "PASSWORD_RESET_COMPLETED", "USER",
                String.valueOf(user.getId()), "sesiones revocadas", ip);
        systemMail.sendNotice(user.getEmail(), "Contraseña actualizada",
                "Tu contraseña se restableció correctamente y se cerraron todas las sesiones abiertas. "
                        + "Si no fuiste tú, contacta de inmediato a tu administrador.");
    }
}
