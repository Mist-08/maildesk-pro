package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/invite")
public class InviteController {

    private final InvitationService invitations;

    public InviteController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/accept")
    public String acceptPage(@RequestParam(required = false) String token, Model model) {
        Optional<Invitation> invitation = invitations.findUsable(token);
        if (invitation.isEmpty()) {
            model.addAttribute("invalid", true);
            return "auth/invite";
        }
        model.addAttribute("token", token);
        model.addAttribute("email", invitation.get().getEmail());
        return "auth/invite";
    }

    @PostMapping("/accept")
    public String accept(@RequestParam String token, @RequestParam String displayName, @RequestParam String password,
                         @RequestParam String passwordConfirm, HttpServletRequest request, Model model,
                         RedirectAttributes flash) {
        try {
            invitations.accept(token, displayName, password, passwordConfirm, ClientIp.of(request));
            flash.addFlashAttribute("success", "Cuenta creada. Inicia sesión con tu correo y contraseña.");
            return "redirect:/login";
        } catch (BusinessException e) {
            Optional<Invitation> invitation = invitations.findUsable(token);
            if (invitation.isEmpty()) {
                model.addAttribute("invalid", true);
            } else {
                model.addAttribute("token", token);
                model.addAttribute("email", invitation.get().getEmail());
                model.addAttribute("displayName", displayName);
            }
            model.addAttribute("error", e.getMessage());
            return "auth/invite";
        }
    }
}
