package com.mycompany.maildesk.admin;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.ClientIp;
import com.mycompany.maildesk.mail.MailStatus;
import com.mycompany.maildesk.messages.MessageRepository;
import com.mycompany.maildesk.messages.MessageStatus;
import com.mycompany.maildesk.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private final SettingsService settings;
    private final MailStatus mailStatus;
    private final MessageRepository messages;
    private final AuditService audit;

    public AdminSettingsController(SettingsService settings, MailStatus mailStatus, MessageRepository messages,
                                   AuditService audit) {
        this.settings = settings;
        this.mailStatus = mailStatus;
        this.messages = messages;
        this.audit = audit;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("defaultSignature", settings.getDefaultSignature());
        model.addAttribute("mailHost", mailStatus.getHostSummary());
        model.addAttribute("mailFrom", mailStatus.getFrom());
        model.addAttribute("queueDepth", messages.countByStatusIn(List.of(MessageStatus.QUEUED, MessageStatus.SENDING)));
        model.addAttribute("uncertainCount", messages.countByStatusIn(List.of(MessageStatus.UNCERTAIN)));
        return "admin/settings";
    }

    @PostMapping("/branding")
    public String branding(@RequestParam String businessName, @RequestParam(required = false) String accentColor,
                           @RequestParam(required = false) String defaultSignature, HttpServletRequest request,
                           RedirectAttributes flash) {
        try {
            settings.updateBranding(businessName, accentColor, defaultSignature);
            audit.record("SETTINGS_UPDATED", "SETTINGS", "branding", businessName, ClientIp.of(request));
            flash.addFlashAttribute("success", "Personalización guardada.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/logo")
    public String logo(@RequestParam("logo") MultipartFile logo, HttpServletRequest request, RedirectAttributes flash) {
        try {
            if (logo == null || logo.isEmpty()) {
                throw new BusinessException("Selecciona una imagen.");
            }
            byte[] head;
            try (InputStream in = logo.getInputStream()) {
                head = in.readNBytes(16);
            }
            try (InputStream in = logo.getInputStream()) {
                settings.storeLogo(in, logo.getContentType(), logo.getSize(), head);
            }
            audit.record("SETTINGS_UPDATED", "SETTINGS", "logo", null, ClientIp.of(request));
            flash.addFlashAttribute("success", "Logotipo actualizado.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            flash.addFlashAttribute("error", "No se pudo guardar el logotipo.");
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/logo/delete")
    public String removeLogo(HttpServletRequest request, RedirectAttributes flash) {
        settings.removeLogo();
        audit.record("SETTINGS_UPDATED", "SETTINGS", "logo", "eliminado", ClientIp.of(request));
        flash.addFlashAttribute("success", "Logotipo eliminado.");
        return "redirect:/admin/settings";
    }
}
