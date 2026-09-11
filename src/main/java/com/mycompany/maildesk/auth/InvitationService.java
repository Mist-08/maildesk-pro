package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.mail.SystemMailService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserService;
import com.mycompany.maildesk.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Invitaciones de usuarios. El enlace contiene un token aleatorio de alta entropía (solo se guarda
 * su hash) que llega únicamente al correo invitado; al aceptarlo el correo queda verificado.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitations;
    private final UserRepository users;
    private final UserService userService;
    private final SystemMailService systemMail;
    private final AuditService audit;
    private final AppProperties properties;
    private final TransactionTemplate tx;

    public InvitationService(InvitationRepository invitations, UserRepository users, UserService userService,
                             SystemMailService systemMail, AuditService audit, AppProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.invitations = invitations;
        this.users = users;
        this.userService = userService;
        this.systemMail = systemMail;
        this.audit = audit;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public void invite(User inviter, String rawEmail, UserRole role, String ip) {
        String email = MailAddressValidator.normalizeSingle(rawEmail == null ? "" : rawEmail.strip(), "correo");
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Ya existe una cuenta con ese correo.");
        }
        String token = Hashing.randomToken();
        Instant now = Instant.now();
        Invitation invitation = tx.execute(status -> {
            invitations.revokePendingFor(email, now);
            Invitation inv = new Invitation();
            inv.setEmail(email);
            inv.setRole(role == null ? UserRole.USER : role);
            inv.setTokenHash(Hashing.sha256Hex(token));
            inv.setInvitedBy(inviter.getId());
            inv.setExpiresAt(now.plus(Duration.ofHours(properties.getSecurity().getInviteTtlHours())));
            return invitations.save(inv);
        });
        String link = properties.getBaseUrl().replaceAll("/+$", "") + "/invite/accept?token=" + token;
        try {
            systemMail.sendInvitation(email, inviter.getDisplayName(), link, properties.getSecurity().getInviteTtlHours());
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> invitations.markUsed(invitation.getId(), Instant.now()));
            throw e;
        }
        audit.recordAs(inviter.getId(), inviter.getEmail(), "USER_INVITED", "INVITATION",
                String.valueOf(invitation.getId()), email + " como " + invitation.getRole(), ip);
    }

    @Transactional(readOnly = true)
    public Optional<Invitation> findUsable(String token) {
        if (token == null || token.length() < 20 || token.length() > 200) {
            return Optional.empty();
        }
        return invitations.findByTokenHash(Hashing.sha256Hex(token)).filter(i -> i.isUsable(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<Invitation> pending() {
        return invitations.findByUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Instant.now());
    }

    @Transactional
    public void revoke(Long invitationId, User actor, String ip) {
        invitations.findById(invitationId).ifPresent(inv -> {
            invitations.markUsed(inv.getId(), Instant.now());
            audit.recordAs(actor.getId(), actor.getEmail(), "INVITATION_REVOKED", "INVITATION",
                    String.valueOf(inv.getId()), inv.getEmail(), ip);
        });
    }

    @Transactional
    public User accept(String token, String displayName, String password, String confirm, String ip) {
        Invitation invitation = findUsable(token)
                .orElseThrow(() -> new BusinessException("La invitación no es válida o ya venció."));
        PasswordPolicy.validate(password, confirm, invitation.getEmail());
        String name = UserService.cleanName(displayName);
        if (invitations.markUsed(invitation.getId(), Instant.now()) != 1) {
            throw new BusinessException("La invitación ya fue utilizada.");
        }
        User user = userService.create(invitation.getEmail(), name, password, invitation.getRole(), UserStatus.ACTIVE);
        audit.recordAs(user.getId(), user.getEmail(), "INVITATION_ACCEPTED", "USER", String.valueOf(user.getId()), null, ip);
        return user;
    }
}
