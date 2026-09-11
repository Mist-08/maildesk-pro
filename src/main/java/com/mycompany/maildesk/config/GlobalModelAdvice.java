package com.mycompany.maildesk.config;

import com.mycompany.maildesk.auth.AuthenticatedUser;
import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.mail.MailStatus;
import com.mycompany.maildesk.settings.SettingsService;
import java.util.Map;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Atributos comunes a todas las vistas (marca, estado del correo, usuario actual). */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SettingsService settings;
    private final MailStatus mailStatus;
    private final CurrentUser currentUser;

    public GlobalModelAdvice(SettingsService settings, MailStatus mailStatus, CurrentUser currentUser) {
        this.settings = settings;
        this.mailStatus = mailStatus;
        this.currentUser = currentUser;
    }

    @ModelAttribute
    public void populate(Map<String, Object> model) {
        model.put("businessName", settings.getBusinessName());
        model.put("accentColor", settings.getAccentColor());
        model.put("hasLogo", settings.hasLogo());
        model.put("mailReal", mailStatus.isReal());
        model.put("mailConfigured", mailStatus.isConfigured());
        AuthenticatedUser user = currentUser.find().orElse(null);
        model.put("currentUser", user);
        model.put("isAdmin", user != null && user.isAdmin());
    }
}
