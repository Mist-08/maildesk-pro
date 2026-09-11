package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/password")
public class PasswordController {

    private final PasswordResetService resetService;

    public PasswordController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    @GetMapping("/forgot")
    public String forgotPage() {
        return "auth/forgot";
    }

    @PostMapping("/forgot")
    public String forgot(@RequestParam String email, HttpServletRequest request, Model model) {
        try {
            resetService.request(email, ClientIp.of(request));
            model.addAttribute("sent", true);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "auth/forgot";
    }

    @GetMapping("/reset")
    public String resetPage(@RequestParam(required = false) String token, Model model) {
        if (!resetService.isTokenUsable(token)) {
            model.addAttribute("invalid", true);
        } else {
            model.addAttribute("token", token);
        }
        return "auth/reset";
    }

    @PostMapping("/reset")
    public String reset(@RequestParam String token, @RequestParam String password, @RequestParam String passwordConfirm,
                        HttpServletRequest request, Model model, RedirectAttributes flash) {
        try {
            resetService.reset(token, password, passwordConfirm, ClientIp.of(request));
            flash.addFlashAttribute("success", "Contraseña actualizada. Inicia sesión de nuevo.");
            return "redirect:/login";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (resetService.isTokenUsable(token)) {
                model.addAttribute("token", token);
            } else {
                model.addAttribute("invalid", true);
            }
            return "auth/reset";
        }
    }
}
