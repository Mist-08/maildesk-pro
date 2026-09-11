package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.settings.SettingsService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Inicio de sesión en dos pasos: contraseña y, después, código de verificación por correo. */
@Controller
public class AuthController {

    private final LoginService loginService;
    private final ChallengeService challenges;
    private final SessionAuthenticator sessions;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final UserService userService;
    private final SettingsService settings;
    private final AuditService audit;
    private final AppProperties properties;

    public AuthController(LoginService loginService, ChallengeService challenges, SessionAuthenticator sessions,
                          CurrentUser currentUser, UserRepository users, UserService userService,
                          SettingsService settings, AuditService audit, AppProperties properties) {
        this.loginService = loginService;
        this.challenges = challenges;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.users = users;
        this.userService = userService;
        this.settings = settings;
        this.audit = audit;
        this.properties = properties;
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        if (currentUser.find().isPresent()) {
            return "redirect:/";
        }
        if (!userService.activeAdminExists() && !settings.isSetupCompleted()) {
            return "redirect:/setup";
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpServletRequest request,
                        Model model) {
        String ip = ClientIp.of(request);
        try {
            User user = loginService.authenticatePassword(email, password, ip);
            PendingChallenge pending = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, ip,
                    "inicio de sesión");
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute(PendingChallenge.LOGIN_KEY, pending);
            return "redirect:/login/verify";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "auth/login";
        }
    }

    @GetMapping("/login/verify")
    public String verifyPage(HttpServletRequest request, Model model) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/login";
        }
        populateVerify(model, pending, "/login");
        return "auth/verify";
    }

    @PostMapping("/login/verify")
    public String verify(@RequestParam String code, HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes flash) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/login";
        }
        String ip = ClientIp.of(request);
        ChallengeService.Verification result = challenges.verify(pending, code);
        switch (result.outcome()) {
            case SUCCESS -> {
                try {
                    User user = loginService.completeLogin(pending.userId(), ip);
                    sessions.establish(request, response, user);
                    return "redirect:/";
                } catch (BusinessException e) {
                    clearPending(request);
                    flash.addFlashAttribute("error", e.getMessage());
                    return "redirect:/login";
                }
            }
            case INVALID_CODE -> {
                audit.recordAs(pending.userId(), null, "OTP_FAILED", "CHALLENGE", String.valueOf(pending.challengeId()),
                        "código incorrecto", ip);
                model.addAttribute("error", "Código incorrecto. Te quedan " + result.remainingAttempts()
                        + " intento(s).");
                populateVerify(model, pending, "/login");
                return "auth/verify";
            }
            default -> {
                audit.recordAs(pending.userId(), null, "OTP_FAILED", "CHALLENGE", String.valueOf(pending.challengeId()),
                        result.outcome().name(), ip);
                clearPending(request);
                flash.addFlashAttribute("error", result.outcome() == ChallengeService.Outcome.TOO_MANY_ATTEMPTS
                        ? "Se agotaron los intentos para este código. Inicia sesión de nuevo."
                        : "El código venció o ya fue utilizado. Inicia sesión de nuevo.");
                return "redirect:/login";
            }
        }
    }

    @PostMapping("/login/resend")
    public String resend(HttpServletRequest request, RedirectAttributes flash) {
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/login";
        }
        String ip = ClientIp.of(request);
        try {
            User user = users.findById(pending.userId()).orElseThrow(() -> new BusinessException("La sesión expiró."));
            PendingChallenge renewed = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, ip,
                    "inicio de sesión");
            request.getSession().setAttribute(PendingChallenge.LOGIN_KEY, renewed);
            flash.addFlashAttribute("success", "Enviamos un nuevo código. El anterior quedó invalidado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/login/verify";
    }

    @PostMapping("/login/cancel")
    public String cancel(HttpServletRequest request) {
        PendingChallenge pending = pending(request);
        challenges.cancel(pending);
        sessions.terminate(request);
        return "redirect:/login";
    }

    private PendingChallenge pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PendingChallenge.LOGIN_KEY);
        if (value instanceof PendingChallenge pending) {
            if (Duration.between(pending.issuedAt(), Instant.now()).getSeconds()
                    > properties.getSecurity().getOtpTtlSeconds() + 60L) {
                session.removeAttribute(PendingChallenge.LOGIN_KEY);
                return null;
            }
            return pending;
        }
        return null;
    }

    private void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PendingChallenge.LOGIN_KEY);
        }
    }

    private void populateVerify(Model model, PendingChallenge pending, String basePath) {
        long elapsed = Duration.between(pending.issuedAt(), Instant.now()).getSeconds();
        long wait = Math.max(0, properties.getSecurity().getOtpResendIntervalSeconds() - elapsed);
        model.addAttribute("maskedEmail", pending.maskedEmail());
        model.addAttribute("resendWait", wait);
        model.addAttribute("ttlMinutes", properties.getSecurity().getOtpTtlSeconds() / 60);
        model.addAttribute("basePath", basePath);
        model.addAttribute("verifyTitle", "Verificación en dos pasos");
    }
}
