package com.mycompany.maildesk.dashboard;

import com.mycompany.maildesk.audit.AuditRepository;
import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.contacts.ContactRepository;
import com.mycompany.maildesk.messages.MessageRepository;
import com.mycompany.maildesk.messages.MessageStatus;
import com.mycompany.maildesk.templates.MailTemplateRepository;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Panel con métricas reales calculadas sobre los datos del usuario (y globales para administradores). */
@Controller
public class DashboardController {

    private final MessageRepository messages;
    private final ContactRepository contacts;
    private final MailTemplateRepository templates;
    private final UserRepository users;
    private final AuditRepository audit;
    private final CurrentUser currentUser;
    private final AppProperties properties;

    public DashboardController(MessageRepository messages, ContactRepository contacts, MailTemplateRepository templates,
                               UserRepository users, AuditRepository audit, CurrentUser currentUser,
                               AppProperties properties) {
        this.messages = messages;
        this.contacts = contacts;
        this.templates = templates;
        this.users = users;
        this.audit = audit;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Long ownerId = currentUser.id();
        Instant weekAgo = Instant.now().minus(Duration.ofDays(7));
        Instant dayAgo = Instant.now().minus(Duration.ofHours(24));
        List<MessageStatus> failedLike = List.of(MessageStatus.FAILED, MessageStatus.UNCERTAIN);

        model.addAttribute("drafts", messages.countByOwnerIdAndStatus(ownerId, MessageStatus.DRAFT));
        model.addAttribute("queued", messages.countByOwnerIdAndStatusIn(ownerId, List.of(MessageStatus.QUEUED, MessageStatus.SENDING)));
        model.addAttribute("sentTotal", messages.countByOwnerIdAndStatus(ownerId, MessageStatus.SENT));
        model.addAttribute("sentWeek", messages.countByOwnerIdAndStatusAndSentAtAfter(ownerId, MessageStatus.SENT, weekAgo));
        model.addAttribute("failedTotal", messages.countByOwnerIdAndStatusIn(ownerId, failedLike));
        model.addAttribute("failedWeek", messages.countByOwnerIdAndStatusInAndUpdatedAtAfter(ownerId, failedLike, weekAgo));
        model.addAttribute("uncertain", messages.countByOwnerIdAndStatus(ownerId, MessageStatus.UNCERTAIN));
        model.addAttribute("quotaUsed", messages.countByOwnerIdAndQueuedAtAfter(ownerId, dayAgo));
        model.addAttribute("quotaMax", properties.getMail().getQuotaPerUserPerDay());
        model.addAttribute("contactCount", contacts.countByOwnerId(ownerId));
        model.addAttribute("templateCount", templates.countByOwnerId(ownerId));
        model.addAttribute("recentFailed", messages.findTop5ByOwnerIdAndStatusInOrderByUpdatedAtDesc(ownerId, failedLike));
        model.addAttribute("recentSent", messages.findTop5ByOwnerIdAndStatusInOrderByUpdatedAtDesc(ownerId, List.of(MessageStatus.SENT)));

        if (currentUser.require().isAdmin()) {
            model.addAttribute("activeUsers", users.countByRoleAndStatus(UserRole.USER, UserStatus.ACTIVE)
                    + users.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE));
            model.addAttribute("globalQueue", messages.countByStatusIn(List.of(MessageStatus.QUEUED, MessageStatus.SENDING)));
            model.addAttribute("recentAudit", audit.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 6)).getContent());
        }
        return "dashboard";
    }
}
