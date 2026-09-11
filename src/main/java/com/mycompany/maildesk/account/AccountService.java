package com.mycompany.maildesk.account;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.auth.PasswordPolicy;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.mail.SystemMailService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final HtmlSanitizer sanitizer;
    private final AuditService audit;
    private final SystemMailService systemMail;

    public AccountService(UserRepository users, UserService userService, PasswordEncoder encoder,
                          HtmlSanitizer sanitizer, AuditService audit, SystemMailService systemMail) {
        this.users = users;
        this.userService = userService;
        this.encoder = encoder;
        this.sanitizer = sanitizer;
        this.audit = audit;
        this.systemMail = systemMail;
    }

    @Transactional
    public User updateProfile(Long userId, String displayName, String ip) {
        User user = users.findById(userId).orElseThrow();
        user.setDisplayName(UserService.cleanName(displayName));
        audit.record("PROFILE_UPDATED", "USER", String.valueOf(userId), null, ip);
        return users.save(user);
    }

    @Transactional
    public User updateSignature(Long userId, String signatureHtml, String ip) {
        User user = users.findById(userId).orElseThrow();
        user.setSignatureHtml(sanitizer.sanitize(signatureHtml));
        audit.record("SIGNATURE_UPDATED", "USER", String.valueOf(userId), null, ip);
        return users.save(user);
    }

    /** Reautenticación: exige la contraseña actual. */
    @Transactional(readOnly = true)
    public User reauthenticate(Long userId, String currentPassword) {
        User user = users.findById(userId).orElseThrow();
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("La contraseña actual no es correcta.");
        }
        return user;
    }

    @Transactional
    public User changePassword(Long userId, String currentPassword, String newPassword, String confirm, String ip) {
        User user = reauthenticate(userId, currentPassword);
        PasswordPolicy.validate(newPassword, confirm, user.getEmail());
        User updated = userService.updatePasswordAndRevokeSessions(userId, newPassword);
        audit.record("PASSWORD_CHANGED", "USER", String.valueOf(userId), "otras sesiones revocadas", ip);
        systemMail.sendNotice(user.getEmail(), "Contraseña cambiada",
                "Tu contraseña de MailDesk Pro se cambió y las demás sesiones se cerraron. Si no fuiste tú, contacta a tu administrador.");
        return updated;
    }

    @Transactional(readOnly = true)
    public String validateNewEmail(Long userId, String rawEmail) {
        String email = MailAddressValidator.normalizeSingle(rawEmail == null ? "" : rawEmail.strip(), "nuevo correo");
        User user = users.findById(userId).orElseThrow();
        if (user.getEmail().equalsIgnoreCase(email)) {
            throw new BusinessException("El nuevo correo es igual al actual.");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Ese correo ya está en uso.");
        }
        return email;
    }

    @Transactional
    public User applyEmailChange(Long userId, String newEmail, String ip) {
        User user = users.findById(userId).orElseThrow();
        if (users.existsByEmailIgnoreCase(newEmail)) {
            throw new BusinessException("Ese correo ya está en uso.");
        }
        String previous = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerifiedAt(java.time.Instant.now());
        User saved = users.save(user);
        audit.record("EMAIL_CHANGED", "USER", String.valueOf(userId), previous + " -> " + newEmail, ip);
        systemMail.sendNotice(previous, "Correo de acceso cambiado",
                "El correo de tu cuenta en MailDesk Pro cambió a " + newEmail + ". Si no fuiste tú, contacta a tu administrador.");
        return saved;
    }
}
