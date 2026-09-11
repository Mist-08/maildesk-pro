package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.settings.SettingsService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserService;
import com.mycompany.maildesk.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta inicial del administrador, de un solo uso: exige el secreto externo {@code APP_SETUP_SECRET}
 * y la verificación del correo configurado en {@code APP_INITIAL_ADMIN_EMAIL}.
 */
@Service
public class SetupService {

    private final UserRepository users;
    private final UserService userService;
    private final SettingsService settings;
    private final AppProperties properties;
    private final PasswordEncoder encoder;
    private final RateLimitService rateLimits;
    private final AuditService audit;

    public SetupService(UserRepository users, UserService userService, SettingsService settings,
                        AppProperties properties, PasswordEncoder encoder, RateLimitService rateLimits,
                        AuditService audit) {
        this.users = users;
        this.userService = userService;
        this.settings = settings;
        this.properties = properties;
        this.encoder = encoder;
        this.rateLimits = rateLimits;
        this.audit = audit;
    }

    /** El alta inicial solo está disponible mientras no exista un administrador activo. */
    public boolean isAvailable() {
        return !userService.activeAdminExists() && !settings.isSetupCompleted();
    }

    public String initialAdminEmail() {
        String email = properties.getInitialAdminEmail();
        return email == null ? "" : email.strip().toLowerCase();
    }

    @Transactional
    public User begin(String setupSecret, String displayName, String password, String confirm, String ip) {
        if (!isAvailable()) {
            throw new BusinessException("El alta inicial ya fue completada.");
        }
        if (rateLimits.checkAndRecord("setup:ip:" + ip, 10, Duration.ofHours(1))) {
            throw new BusinessException("Demasiados intentos. Espera antes de volver a intentarlo.");
        }
        String configured = properties.getSetupSecret();
        if (configured == null || configured.strip().length() < 16) {
            throw new BusinessException("APP_SETUP_SECRET no está configurado (mínimo 16 caracteres). "
                    + "Defínelo en el entorno o en .env y reinicia la aplicación.");
        }
        if (!Hashing.constantTimeEquals(configured.strip(), setupSecret == null ? "" : setupSecret.strip())) {
            audit.recordAs(null, initialAdminEmail(), "SETUP_SECRET_REJECTED", "SETUP", null, null, ip);
            throw new BusinessException("Secreto de instalación incorrecto.");
        }
        String email = initialAdminEmail();
        if (email.isBlank() || !MailAddressValidator.isValid(email)) {
            throw new BusinessException("APP_INITIAL_ADMIN_EMAIL no está configurado o no es válido.");
        }
        PasswordPolicy.validate(password, confirm, email);
        String name = UserService.cleanName(displayName);

        Optional<User> existing = users.findByEmailIgnoreCase(email);
        User user;
        if (existing.isPresent()) {
            user = existing.get();
            if (user.getStatus() == UserStatus.ACTIVE) {
                throw new BusinessException("Esa cuenta ya está activa.");
            }
            user.setDisplayName(name);
            user.setPasswordHash(encoder.encode(password));
            user.setRole(UserRole.ADMIN);
            user.setStatus(UserStatus.PENDING_VERIFICATION);
            user = users.save(user);
        } else {
            user = userService.create(email, name, password, UserRole.ADMIN, UserStatus.PENDING_VERIFICATION);
        }
        audit.recordAs(user.getId(), user.getEmail(), "SETUP_STARTED", "USER", String.valueOf(user.getId()), null, ip);
        return user;
    }

    @Transactional
    public User complete(Long userId, String ip) {
        if (userService.activeAdminExists()) {
            throw new BusinessException("Ya existe un administrador activo.");
        }
        User user = users.findById(userId).orElseThrow(() -> new BusinessException("La sesión de alta expiró."));
        if (!user.getEmail().equalsIgnoreCase(initialAdminEmail())) {
            throw new BusinessException("El correo no coincide con el administrador inicial configurado.");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.ADMIN);
        user.setEmailVerifiedAt(Instant.now());
        user.setLastLoginAt(Instant.now());
        users.save(user);
        settings.set(SettingsService.SETUP_COMPLETED, "true");
        audit.recordAs(user.getId(), user.getEmail(), "SETUP_COMPLETED", "USER", String.valueOf(user.getId()), null, ip);
        return user;
    }
}
