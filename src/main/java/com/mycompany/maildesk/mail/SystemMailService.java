package com.mycompany.maildesk.mail;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.settings.SettingsService;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Correos del sistema (códigos de verificación, invitaciones, recuperación, avisos). Se envían de
 * forma sincrónica porque el flujo del usuario depende de ellos. Nunca se registran sus contenidos.
 */
@Service
public class SystemMailService {

    private static final Logger log = LoggerFactory.getLogger(SystemMailService.class);

    private final MailTransport transport;
    private final TemplateEngine templateEngine;
    private final SettingsService settings;

    public SystemMailService(MailTransport transport, TemplateEngine templateEngine, SettingsService settings) {
        this.transport = transport;
        this.templateEngine = templateEngine;
        this.settings = settings;
    }

    public void sendVerificationCode(String to, String code, String purposeLabel, int ttlMinutes) {
        String business = settings.getBusinessName();
        String subject = business + " · Código de verificación";
        String text = "Tu código de verificación (" + purposeLabel + ") es: " + code
                + "\n\nCaduca en " + ttlMinutes + " minutos y solo puede usarse una vez."
                + "\nSi no solicitaste este código, ignora este mensaje.";
        String html = render("mail/codigo", Map.of("code", code, "purpose", purposeLabel, "ttl", ttlMinutes));
        deliver(OutgoingMail.system(to, subject, text, html), "código de verificación");
    }

    public void sendInvitation(String to, String inviterName, String link, int ttlHours) {
        String business = settings.getBusinessName();
        String subject = business + " · Invitación a MailDesk Pro";
        String text = inviterName + " te invitó a " + business + " en MailDesk Pro.\n\n"
                + "Acepta la invitación en el siguiente enlace (válido " + ttlHours + " horas):\n" + link;
        String html = render("mail/invitacion", Map.of("inviter", inviterName, "link", link, "ttl", ttlHours));
        deliver(OutgoingMail.system(to, subject, text, html), "invitación");
    }

    public void sendPasswordReset(String to, String link, int ttlMinutes) {
        String business = settings.getBusinessName();
        String subject = business + " · Restablecer contraseña";
        String text = "Recibimos una solicitud para restablecer tu contraseña.\n\n"
                + "Enlace (válido " + ttlMinutes + " minutos, un solo uso):\n" + link
                + "\n\nSi no fuiste tú, ignora este mensaje; tu contraseña no cambiará.";
        String html = render("mail/restablecer", Map.of("link", link, "ttl", ttlMinutes));
        deliver(OutgoingMail.system(to, subject, text, html), "restablecimiento de contraseña");
    }

    public void sendNotice(String to, String subject, String text) {
        String html = render("mail/aviso", Map.of("title", subject, "text", text));
        SendOutcome outcome = transport.send(OutgoingMail.system(to, settings.getBusinessName() + " · " + subject, text, html));
        if (!outcome.accepted()) {
            log.warn("No se pudo enviar aviso ({}): {}", outcome.kind(), outcome.error());
        }
    }

    private void deliver(OutgoingMail mail, String label) {
        SendOutcome outcome = transport.send(mail);
        if (!outcome.accepted()) {
            log.warn("No se pudo enviar {} ({}): {}", label, outcome.kind(), outcome.error());
            throw new BusinessException("No se pudo enviar el correo de " + label
                    + ". El servidor de correo no está disponible o está pendiente de configuración.");
        }
    }

    private String render(String template, Map<String, Object> vars) {
        Context ctx = new Context(Locale.forLanguageTag("es-MX"));
        vars.forEach(ctx::setVariable);
        ctx.setVariable("businessName", HtmlUtils.htmlEscape(settings.getBusinessName()));
        return templateEngine.process(template, ctx);
    }
}
