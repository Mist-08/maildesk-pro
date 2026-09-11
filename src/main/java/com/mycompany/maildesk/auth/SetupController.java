package com.mycompany.maildesk.auth;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
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
@RequestMapping("/setup")
public class SetupController {

    private final SetupService setupService;
    private final ChallengeService challenges;
    private final SessionAuthenticator sessions;
    private final UserRepository users;
    private final AppProperties properties;

    public SetupController(SetupService setupService, ChallengeService challenges, SessionAuthenticator sessions,
                           UserRepository users, AppProperties properties) {
        this.setupService = setupService;
        this.challenges = challenges;
        this.sessions = sessions;
        this.users = users;
        this.properties = properties;
    }

    @GetMapping
    public String page(Model model) {
        ensureAvailable();
        model.addAttribute("adminEmail", setupService.initialAdminEmail());
        return "auth/setup";
    }

    @PostMapping
    public String begin(@RequestParam String setupSecret, @RequestParam String displayName,
                        @RequestParam String password, @RequestParam String passwordConfirm,
                        HttpServletRequest request, Model model) {
        ensureAvailable();
        String ip = ClientIp.of(request);
        try {
            User user = setupService.begin(setupSecret, displayName, password, passwordConfirm, ip);
            PendingChallenge pending = challenges.issue(user, ChallengePurpose.ADMIN_SETUP, user.getEmail(), null, ip,
                    "alta del administrador");
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            session.setAttribute(PendingChallenge.SETUP_KEY, pending);
            return "redirect:/setup/verify";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("adminEmail", setupService.initialAdminEmail());
            model.addAttribute("displayName", displayName);
            return "auth/setup";
        }
    }

    @GetMapping("/verify")
    public String verifyPage(HttpServletRequest request, Model model) {
        ensureAvailable();
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/setup";
        }
        populate(model, pending);
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String code, HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes flash) {
        ensureAvailable();
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/setup";
        }
        ChallengeService.Verification result = challenges.verify(pending, code);
        if (result.success()) {
            try {
                User user = setupService.complete(pending.userId(), ClientIp.of(request));
                sessions.establish(request, response, user);
                flash.addFlashAttribute("success", "Cuenta de administrador verificada. ¡Bienvenido!");
                return "redirect:/";
            } catch (BusinessException e) {
                flash.addFlashAttribute("error", e.getMessage());
                return "redirect:/setup";
            }
        }
        if (result.outcome() == ChallengeService.Outcome.INVALID_CODE) {
            model.addAttribute("error", "Código incorrecto. Te quedan " + result.remainingAttempts() + " intento(s).");
            populate(model, pending);
            return "auth/verify";
        }
        request.getSession().removeAttribute(PendingChallenge.SETUP_KEY);
        flash.addFlashAttribute("error", "El código venció, se agotó o ya fue usado. Vuelve a empezar.");
        return "redirect:/setup";
    }

    @PostMapping("/resend")
    public String resend(HttpServletRequest request, RedirectAttributes flash) {
        ensureAvailable();
        PendingChallenge pending = pending(request);
        if (pending == null) {
            return "redirect:/setup";
        }
        try {
            User user = users.findById(pending.userId()).orElseThrow(() -> new BusinessException("La sesión expiró."));
            PendingChallenge renewed = challenges.issue(user, ChallengePurpose.ADMIN_SETUP, user.getEmail(), null,
                    ClientIp.of(request), "alta del administrador");
            request.getSession().setAttribute(PendingChallenge.SETUP_KEY, renewed);
            flash.addFlashAttribute("success", "Enviamos un nuevo código. El anterior quedó invalidado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/setup/verify";
    }

    @PostMapping("/cancel")
    public String cancel(HttpServletRequest request) {
        challenges.cancel(pending(request));
        sessions.terminate(request);
        return "redirect:/setup";
    }

    private void ensureAvailable() {
        if (!setupService.isAvailable()) {
            throw new NotFoundException("El alta inicial no está disponible.");
        }
    }

    private PendingChallenge pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(PendingChallenge.SETUP_KEY) instanceof PendingChallenge p) {
            return p;
        }
        return null;
    }

    private void populate(Model model, PendingChallenge pending) {
        long elapsed = Duration.between(pending.issuedAt(), Instant.now()).getSeconds();
        model.addAttribute("maskedEmail", pending.maskedEmail());
        model.addAttribute("resendWait", Math.max(0, properties.getSecurity().getOtpResendIntervalSeconds() - elapsed));
        model.addAttribute("ttlMinutes", properties.getSecurity().getOtpTtlSeconds() / 60);
        model.addAttribute("basePath", "/setup");
        model.addAttribute("verifyTitle", "Verifica tu correo de administrador");
    }
}
