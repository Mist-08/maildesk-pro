package com.mycompany.maildesk.templates;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.contacts.Contact;
import com.mycompany.maildesk.mail.MailAddressValidator;
import com.mycompany.maildesk.settings.SettingsService;
import com.mycompany.maildesk.user.User;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateService {

    private final MailTemplateRepository templates;
    private final HtmlSanitizer sanitizer;
    private final SettingsService settings;
    private final AuditService audit;

    public TemplateService(MailTemplateRepository templates, HtmlSanitizer sanitizer, SettingsService settings,
                           AuditService audit) {
        this.templates = templates;
        this.sanitizer = sanitizer;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<MailTemplate> list(Long ownerId, String q, Pageable pageable) {
        return templates.findByOwnerIdAndNameContainingIgnoreCaseOrderByNameAsc(ownerId, q == null ? "" : q.strip(), pageable);
    }

    @Transactional(readOnly = true)
    public List<MailTemplate> all(Long ownerId) {
        return templates.findByOwnerIdOrderByNameAsc(ownerId);
    }

    @Transactional(readOnly = true)
    public MailTemplate getOwned(Long id, Long ownerId) {
        return templates.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new NotFoundException("Plantilla no encontrada"));
    }

    @Transactional
    public MailTemplate save(Long ownerId, Long id, String name, String subject, String bodyHtml, String ip) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new BusinessException("El nombre de la plantilla debe tener entre 1 y 120 caracteres.");
        }
        MailAddressValidator.rejectHeaderInjection(subject, "asunto");
        String cleanSubject = subject == null ? "" : subject.strip();
        if (cleanSubject.isEmpty() || cleanSubject.length() > 500) {
            throw new BusinessException("El asunto debe tener entre 1 y 500 caracteres.");
        }
        MailTemplate template = id == null ? new MailTemplate() : getOwned(id, ownerId);
        if ((id == null || !template.getName().equalsIgnoreCase(cleanName))
                && templates.existsByOwnerIdAndNameIgnoreCase(ownerId, cleanName)) {
            throw new BusinessException("Ya tienes una plantilla con ese nombre.");
        }
        template.setOwnerId(ownerId);
        template.setName(cleanName);
        template.setSubject(cleanSubject);
        template.setBodyHtml(sanitizer.sanitize(bodyHtml));
        MailTemplate saved = templates.save(template);
        audit.record(id == null ? "TEMPLATE_CREATED" : "TEMPLATE_UPDATED", "TEMPLATE", String.valueOf(saved.getId()), cleanName, ip);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long ownerId, String ip) {
        MailTemplate template = getOwned(id, ownerId);
        templates.delete(template);
        audit.record("TEMPLATE_DELETED", "TEMPLATE", String.valueOf(id), template.getName(), ip);
    }

    public TemplateRenderer.Rendered render(MailTemplate template, Contact contact, User sender) {
        Map<String, String> values = new HashMap<>();
        values.put("nombre", contact == null ? "" : contact.getFullName());
        values.put("email", contact == null ? "" : contact.getEmail());
        values.put("empresa", contact == null || contact.getCompany() == null ? "" : contact.getCompany());
        values.put("negocio", settings.getBusinessName());
        values.put("remitente", sender == null ? "" : sender.getDisplayName());
        values.put("fecha", LocalDate.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"))));
        return TemplateRenderer.render(template.getSubject(), template.getBodyHtml(), values);
    }

    public TemplateRenderer.Rendered preview(MailTemplate template, User sender) {
        Map<String, String> values = new HashMap<>();
        values.put("nombre", "Ana Ejemplo");
        values.put("email", "ana@ejemplo.com");
        values.put("empresa", "Empresa Ejemplo");
        values.put("negocio", settings.getBusinessName());
        values.put("remitente", sender == null ? "" : sender.getDisplayName());
        values.put("fecha", LocalDate.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"))));
        return TemplateRenderer.render(template.getSubject(), template.getBodyHtml(), values);
    }
}
