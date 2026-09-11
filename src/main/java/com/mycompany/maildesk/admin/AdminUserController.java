package com.mycompany.maildesk.admin;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.auth.InvitationService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserService;
import com.mycompany.maildesk.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final UserService userService;
    private final InvitationService invitations;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AdminUserController(UserRepository users, UserService userService, InvitationService invitations,
                               AuditService audit, CurrentUser currentUser) {
        this.users = users;
        this.userService = userService;
        this.invitations = invitations;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", users.findAllByOrderByCreatedAtAsc(PageRequest.of(Math.max(0, page), 25)));
        model.addAttribute("invitations", invitations.pending());
        model.addAttribute("roles", UserRole.values());
        return "admin/users";
    }

    @PostMapping("/invite")
    public String invite(@RequestParam String email, @RequestParam(defaultValue = "USER") UserRole role,
                         HttpServletRequest request, RedirectAttributes flash) {
        try {
            invitations.invite(currentUser.load(), email, role, ClientIp.of(request));
            flash.addFlashAttribute("success", "Invitación enviada a " + email.strip().toLowerCase() + ".");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/invitations/{id}/revoke")
    public String revoke(@PathVariable Long id, HttpServletRequest request, RedirectAttributes flash) {
        invitations.revoke(id, currentUser.load(), ClientIp.of(request));
        flash.addFlashAttribute("success", "Invitación revocada.");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam UserStatus status, HttpServletRequest request,
                         RedirectAttributes flash) {
        try {
            if (status == UserStatus.PENDING_VERIFICATION) {
                throw new BusinessException("Estado no permitido.");
            }
            User updated = userService.setStatus(id, status, currentUser.id());
            audit.record(status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED", "USER", String.valueOf(id),
                    updated.getEmail(), ClientIp.of(request));
            flash.addFlashAttribute("success", "Estado actualizado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/role")
    public String role(@PathVariable Long id, @RequestParam UserRole role, HttpServletRequest request,
                       RedirectAttributes flash) {
        try {
            User updated = userService.setRole(id, role, currentUser.id());
            audit.record("USER_ROLE_CHANGED", "USER", String.valueOf(id), updated.getEmail() + " -> " + role,
                    ClientIp.of(request));
            flash.addFlashAttribute("success", "Rol actualizado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
