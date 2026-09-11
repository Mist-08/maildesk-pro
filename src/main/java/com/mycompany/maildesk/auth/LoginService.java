package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Primer paso del acceso: correo y contraseña, con bloqueo por intentos y límites por IP. */
@Service
public class LoginService {

    private static final String GENERIC_ERROR = "Correo o contraseña incorrectos.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RateLimitService rateLimits;
    private final AuditService audit;
    private final AppProperties properties;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder encoder, RateLimitService rateLimits,
                        AuditService audit, AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.rateLimits = rateLimits;
        this.audit = audit;
        this.properties = properties;
        this.dummyHash = encoder.encode("dummy-password-to-equalize-timing");
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public User authenticatePassword(String rawEmail, String password, String ip) {
        AppProperties.Security sec = properties.getSecurity();
        if (rateLimits.checkAndRecord("login:ip:" + ip, sec.getLoginMaxAttemptsPerIpPer15min(), Duration.ofMinutes(15))) {
            throw new BusinessException("Demasiados intentos desde esta conexión. Espera unos minutos.");
        }
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase();
        Optional<User> found = users.findByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);
            audit.recordAs(null, email, "LOGIN_FAILED", "USER", null, "cuenta inexistente", ip);
            throw new BusinessException(GENERIC_ERROR);
        }
        User user = found.get();
        Instant now = Instant.now();
        if (user.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            audit.recordAs(user.getId(), user.getEmail(), "LOGIN_FAILED", "USER", String.valueOf(user.getId()), "cuenta bloqueada", ip);
            throw new BusinessException("Cuenta bloqueada temporalmente por intentos fallidos. Intenta en " + minutes + " min.");
        }
        boolean matches = encoder.matches(password == null ? "" : password, user.getPasswordHash());
        if (!matches) {
            user.setFailedLoginCount(user.getFailedLoginCount() + 1);
            if (user.getFailedLoginCount() >= sec.getLoginMaxFailures()) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(sec.getLoginLockMinutes())));
                user.setFailedLoginCount(0);
            }
            users.save(user);
            audit.recordAs(user.getId(), user.getEmail(), "LOGIN_FAILED", "USER", String.valueOf(user.getId()), "contraseña incorrecta", ip);
            throw new BusinessException(GENERIC_ERROR);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            audit.recordAs(user.getId(), user.getEmail(), "LOGIN_FAILED", "USER", String.valueOf(user.getId()), "cuenta no activa", ip);
            throw new BusinessException("Esta cuenta no está activa. Contacta a tu administrador.");
        }
        return user;
    }

    @Transactional
    public User completeLogin(Long userId, String ip) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException("La sesión expiró."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Esta cuenta no está activa.");
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        users.save(user);
        audit.recordAs(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "USER", String.valueOf(user.getId()), null, ip);
        return user;
    }
}
