package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.MailAddressValidator;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Borradores, cola de salida e historial. Toda operación exige que el mensaje pertenezca al usuario. */
@Service
public class MessageService {

    public static final Map<String, List<MessageStatus>> BOXES = Map.of(
            "drafts", List.of(MessageStatus.DRAFT),
            "outbox", List.of(MessageStatus.QUEUED, MessageStatus.SENDING),
            "sent", List.of(MessageStatus.SENT),
            "failed", List.of(MessageStatus.FAILED, MessageStatus.UNCERTAIN, MessageStatus.CANCELLED),
            "all", List.of(MessageStatus.values()));

    private final MessageRepository messages;
    private final AttachmentService attachments;
    private final HtmlSanitizer sanitizer;
    private final AppProperties properties;
    private final AuditService audit;

    public MessageService(MessageRepository messages, AttachmentService attachments, HtmlSanitizer sanitizer,
                          AppProperties properties, AuditService audit) {
        this.messages = messages;
        this.attachments = attachments;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Message getOwned(Long id, Long ownerId) {
        return messages.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new NotFoundException("Mensaje no encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<Message> list(Long ownerId, String box, String q, Pageable pageable) {
        Collection<MessageStatus> statuses = BOXES.getOrDefault(box == null ? "all" : box, BOXES.get("all"));
        String pattern = "%" + (q == null ? "" : q.strip().toLowerCase()) + "%";
        return messages.search(ownerId, statuses, pattern, pageable);
    }

    @Transactional
    public Message createDraft(Long ownerId) {
        Message message = new Message();
        message.setOwnerId(ownerId);
        message.setStatus(MessageStatus.DRAFT);
        return messages.save(message);
    }

    @Transactional
    public Message saveDraft(Long id, Long ownerId, DraftForm form) {
        Message message = getOwned(id, ownerId);
        if (!message.getStatus().isEditable()) {
            throw new BusinessException("Este mensaje ya no es un borrador y no puede editarse.");
        }
        apply(message, form);
        return messages.save(message);
    }

    private void apply(Message message, DraftForm form) {
        MailAddressValidator.rejectHeaderInjection(form.getTo(), "Para");
        MailAddressValidator.rejectHeaderInjection(form.getCc(), "CC");
        MailAddressValidator.rejectHeaderInjection(form.getBcc(), "CCO");
        MailAddressValidator.rejectHeaderInjection(form.getSubject(), "asunto");
        message.setToRecipients(limit(form.getTo(), 4000));
        message.setCcRecipients(limit(form.getCc(), 4000));
        message.setBccRecipients(limit(form.getBcc(), 4000));
        message.setSubject(limit(form.getSubject(), 500));
        message.setContentType(form.getContentType() == null ? ContentType.HTML : form.getContentType());
        String body = form.getBody() == null ? "" : form.getBody();
        if (body.length() > 500_000) {
            throw new BusinessException("El cuerpo del mensaje es demasiado largo.");
        }
        message.setBodyHtml(message.getContentType() == ContentType.HTML ? sanitizer.sanitize(body) : body);
        message.setIncludeSignature(form.isIncludeSignature());
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.strip();
        if (v.length() > max) {
            throw new BusinessException("Un campo supera la longitud máxima permitida.");
        }
        return v;
    }

    /**
     * Pone el borrador en la cola de salida. Es idempotente frente a doble clic: si ya salió del
     * estado borrador, devuelve el mensaje tal cual sin crear duplicados.
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public Message send(Long id, Long ownerId, DraftForm form, String ip) {
        Message message = getOwned(id, ownerId);
        if (!message.getStatus().isEditable()) {
            return message;
        }
        if (form != null) {
            apply(message, form);
            messages.save(message);
        }
        List<String> to = MailAddressValidator.parseList(message.getToRecipients(), "Para");
        List<String> cc = MailAddressValidator.parseList(message.getCcRecipients(), "CC");
        List<String> bcc = MailAddressValidator.parseList(message.getBccRecipients(), "CCO");
        if (to.isEmpty()) {
            throw new BusinessException("Indica al menos un destinatario en \"Para\".");
        }
        int total = to.size() + cc.size() + bcc.size();
        if (total > properties.getMail().getMaxRecipientsPerMessage()) {
            throw new BusinessException("Máximo " + properties.getMail().getMaxRecipientsPerMessage() + " destinatarios por mensaje.");
        }
        if (message.getSubject() == null || message.getSubject().isBlank()) {
            throw new BusinessException("Escribe un asunto.");
        }
        Instant dayStart = Instant.now().minus(Duration.ofHours(24));
        long usedToday = messages.countByOwnerIdAndQueuedAtAfter(ownerId, dayStart);
        if (usedToday >= properties.getMail().getQuotaPerUserPerDay()) {
            throw new BusinessException("Alcanzaste la cuota diaria de " + properties.getMail().getQuotaPerUserPerDay()
                    + " mensajes. Intenta mañana.");
        }
        message.setToRecipients(String.join(", ", to));
        message.setCcRecipients(String.join(", ", cc));
        message.setBccRecipients(String.join(", ", bcc));
        messages.saveAndFlush(message);

        String token = form != null && form.getSubmissionToken() != null && form.getSubmissionToken().matches("[A-Za-z0-9\\-]{8,64}")
                ? form.getSubmissionToken() : UUID.randomUUID().toString();
        int updated;
        try {
            updated = messages.enqueueDraft(id, ownerId, token, Instant.now());
        } catch (DataIntegrityViolationException duplicate) {
            // El mismo token ya se usó (doble envío): no se crea un segundo envío.
            return getOwned(id, ownerId);
        }
        if (updated == 1) {
            audit.record("MESSAGE_QUEUED", "MESSAGE", String.valueOf(id), total + " destinatario(s)", ip);
        }
        return getOwned(id, ownerId);
    }

    @Transactional
    public boolean cancel(Long id, Long ownerId, String ip) {
        getOwned(id, ownerId);
        boolean done = messages.cancelQueued(id, ownerId, Instant.now()) == 1;
        if (done) {
            audit.record("MESSAGE_CANCELLED", "MESSAGE", String.valueOf(id), null, ip);
        }
        return done;
    }

    @Transactional
    public boolean retry(Long id, Long ownerId, String ip) {
        getOwned(id, ownerId);
        boolean done = messages.requeueFailed(id, ownerId, Instant.now()) == 1;
        if (done) {
            audit.record("MESSAGE_RETRIED", "MESSAGE", String.valueOf(id), null, ip);
        }
        return done;
    }

    @Transactional
    public Message duplicate(Long id, Long ownerId, String ip) {
        Message source = getOwned(id, ownerId);
        Message copy = new Message();
        copy.setOwnerId(ownerId);
        copy.setStatus(MessageStatus.DRAFT);
        copy.setToRecipients(source.getToRecipients());
        copy.setCcRecipients(source.getCcRecipients());
        copy.setBccRecipients(source.getBccRecipients());
        copy.setSubject(source.getSubject());
        copy.setBodyHtml(source.getBodyHtml());
        copy.setContentType(source.getContentType());
        copy.setIncludeSignature(source.isIncludeSignature());
        copy = messages.save(copy);
        try {
            attachments.copyAll(source.getId(), copy.getId(), ownerId);
        } catch (IOException e) {
            throw new BusinessException("No se pudieron copiar los adjuntos.");
        }
        audit.record("MESSAGE_DUPLICATED", "MESSAGE", String.valueOf(copy.getId()), "desde " + id, ip);
        return copy;
    }

    @Transactional
    public void delete(Long id, Long ownerId, String ip) {
        Message message = getOwned(id, ownerId);
        if (!message.getStatus().isDeletable()) {
            throw new BusinessException("No se puede eliminar un mensaje en cola o en envío. Cancélalo primero.");
        }
        attachments.deleteFilesFor(id, ownerId);
        messages.delete(message);
        audit.record("MESSAGE_DELETED", "MESSAGE", String.valueOf(id), message.getStatus().name(), ip);
    }

    @Transactional
    public void applyTemplate(Long id, Long ownerId, String subject, String bodyHtml) {
        Message message = getOwned(id, ownerId);
        if (!message.getStatus().isEditable()) {
            throw new BusinessException("Solo se puede aplicar una plantilla a un borrador.");
        }
        MailAddressValidator.rejectHeaderInjection(subject, "asunto");
        message.setSubject(limit(subject, 500));
        message.setContentType(ContentType.HTML);
        message.setBodyHtml(sanitizer.sanitize(bodyHtml));
        messages.save(message);
    }

    @Transactional
    public void setRecipient(Long id, Long ownerId, String email) {
        Message message = getOwned(id, ownerId);
        if (!message.getStatus().isEditable()) {
            throw new BusinessException("Solo se puede modificar un borrador.");
        }
        String normalized = MailAddressValidator.normalizeSingle(email, "Para");
        List<String> current = new java.util.ArrayList<>(message.toList());
        if (!current.contains(normalized)) {
            current.add(normalized);
        }
        message.setToRecipients(String.join(", ", current));
        messages.save(message);
    }
}
