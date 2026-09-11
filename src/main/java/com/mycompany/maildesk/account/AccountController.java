package com.mycompany.maildesk.account;

import com.mycompany.maildesk.auth.ChallengePurpose;
import com.mycompany.maildesk.auth.ChallengeService;
import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.auth.PendingChallenge;
import com.mycompany.maildesk.auth.SessionAuthenticator;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService account;
    private final ChallengeService challenges;
    private final SessionAuthenticator sessions;
    private final CurrentUser currentUser;
    private final AppProperties properties;

    public AccountController(AccountService account, ChallengeService challenges, SessionAuthenticator sessions,
                             CurrentUser currentUser, AppProperties properties) {
        this.account = account;
        this.challenges = challenges;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("user", currentUser.load());
        return "account/index";
    }

    @PostMapping("/profile")
    public String profile(@RequestParam String displayName, HttpServletRequest request, HttpServletResponse response,
                          RedirectAttributes flash) {
        try {
            User user = account.updateProfile(currentUser.id(), displayName, ClientIp.of(request));
            sessions.refresh(request, response, user);
            flash.addFlashAttribute("success", "Perfil actualizado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    @PostMapping("/signature")
    public String signature(@RequestParam String signatureHtml, HttpServletRequest request, RedirectAttributes flash) {
        account.updateSignature(currentUser.id(), signatureHtml, ClientIp.of(request));
        flash.addFlashAttribute("success", "Firma guardada.");
        return "redirect:/account";
    }

    @PostMapping("/password")
    public String password(@RequestParam String currentPassword, @RequestParam String newPassword,
                           @RequestParam String newPasswordConfirm, HttpServletRequest request,
                           HttpServletResponse response, RedirectAttributes flash) {
        try {
            User user = account.changePassword(currentUser.id(), currentPassword, newPassword, newPasswordConfirm,
                    ClientIp.of(request));
            // La época cambió: se renueva esta sesión y se invalidan las demás.
            sessions.establish(request, response, user);
            flash.addFlashAttribute("success", "Contraseña actualizada. Las demás sesiones se cerraron.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account";
    }

    @PostMapping("/email/start")
    public String startEmailChange(@RequestParam String currentPassword, @RequestParam String newEmail,
                                   HttpServletRequest request, RedirectAttributes flash) {
        String ip = ClientIp.of(request);
        try {
            User user = account.reauthenticate(currentUser.id(), currentPassword);
            String email = account.validateNewEmail(user.getId(), newEmail);
            PendingChallenge pending = challenges.issue(user, ChallengePurpose.EMAIL_CHANGE, email, email, ip,
                    "cambio de correo");
            request.getSession().setAttribute(PendingChallenge.EMAIL_CHANGE_KEY, pending);
            request.getSession().setAttribute(PendingChallenge.EMAIL_CHANGE_KEY + "_EMAIL", email);
            return "redirect:/account/email/verify";
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/account";
        }
    }

    @GetMapping("/email/verify")
    public String verifyPage(HttpServletRequest request, Model model) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/account";
        }
        populate(model, pending);
        return "auth/verify";
    }

    @PostMapping("/email/verify")
    public String verify(@RequestParam String code, HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes flash) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/account";
        }
        ChallengeService.Verification result = challenges.verify(pending, code);
        if (result.success()) {
            try {
                User user = account.applyEmailChange(currentUser.id(), result.payload(), ClientIp.of(request));
                request.getSession().removeAttribute(PendingChallenge.EMAIL_CHANGE_KEY);
                sessions.refresh(request, response, user);
                flash.addFlashAttribute("success", "Correo actualizado y verificado.");
            } catch (BusinessException e) {
                flash.addFlashAttribute("error", e.getMessage());
            }
            return "redirect:/account";
        }
        if (result.outcome() == ChallengeService.Outcome.INVALID_CODE) {
            model.addAttribute("error", "Código incorrecto. Te quedan " + result.remainingAttempts() + " intento(s).");
            populate(model, pending);
            return "auth/verify";
        }
        request.getSession().removeAttribute(PendingChallenge.EMAIL_CHANGE_KEY);
        flash.addFlashAttribute("error", "El código venció, se agotó o ya fue usado. Vuelve a solicitar el cambio.");
        return "redirect:/account";
    }

    @PostMapping("/email/resend")
    public String resend(HttpServletRequest request, RedirectAttributes flash) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/account";
        }
        try {
            User user = currentUser.load();
            String email = account.validateNewEmail(user.getId(), pendingEmail(request));
            PendingChallenge renewed = challenges.issue(user, ChallengePurpose.EMAIL_CHANGE, email, email,
                    ClientIp.of(request), "cambio de correo");
            request.getSession().setAttribute(PendingChallenge.EMAIL_CHANGE_KEY, renewed);
            flash.addFlashAttribute("success", "Enviamos un nuevo código.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account/email/verify";
    }

    @PostMapping("/email/cancel")
    public String cancel(HttpServletRequest request) {
        challenges.cancel(pending(request));
        request.getSession().removeAttribute(PendingChallenge.EMAIL_CHANGE_KEY);
        return "redirect:/account";
    }

    private String pendingEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(PendingChallenge.EMAIL_CHANGE_KEY + "_EMAIL");
        return value instanceof String s ? s : "";
    }

    private PendingChallenge pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(PendingChallenge.EMAIL_CHANGE_KEY) instanceof PendingChallenge p
                && p.userId().equals(currentUser.id())) {
            return p;
        }
        return null;
    }

    private void populate(Model model, PendingChallenge pending) {
        long elapsed = Duration.between(pending.issuedAt(), Instant.now()).getSeconds();
        model.addAttribute("maskedEmail", pending.maskedEmail());
        model.addAttribute("resendWait", Math.max(0, properties.getSecurity().getOtpResendIntervalSeconds() - elapsed));
        model.addAttribute("ttlMinutes", properties.getSecurity().getOtpTtlSeconds() / 60);
        model.addAttribute("basePath", "/account/email");
        model.addAttribute("verifyTitle", "Verifica tu nuevo correo");
    }
}
