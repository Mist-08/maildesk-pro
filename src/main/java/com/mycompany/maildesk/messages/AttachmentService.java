package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.common.Hashing;
import com.mycompany.maildesk.common.NotFoundException;
import com.mycompany.maildesk.common.StorageService;
import com.mycompany.maildesk.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

    private static final String AREA = "attachments";

    private final AttachmentRepository attachments;
    private final MessageRepository messages;
    private final StorageService storage;
    private final AppProperties properties;
    private final AuditService audit;

    public AttachmentService(AttachmentRepository attachments, MessageRepository messages, StorageService storage,
                             AppProperties properties, AuditService audit) {
        this.attachments = attachments;
        this.messages = messages;
        this.storage = storage;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Attachment> listFor(Long messageId) {
        return attachments.findByMessageIdOrderByCreatedAtAsc(messageId);
    }

    @Transactional(readOnly = true)
    public Attachment getOwned(Long attachmentId, Long ownerId) {
        return attachments.findByIdAndOwnerId(attachmentId, ownerId)
                .orElseThrow(() -> new NotFoundException("Adjunto no encontrado"));
    }

    public Path pathOf(Attachment attachment) {
        return storage.resolve(AREA + "/" + attachment.getOwnerId(), attachment.getStoredName());
    }

    @Transactional
    public Attachment add(Long messageId, Long ownerId, MultipartFile file, String ip) throws IOException {
        Message message = messages.findByIdAndOwnerId(messageId, ownerId)
                .orElseThrow(() -> new NotFoundException("Mensaje no encontrado"));
        if (!message.getStatus().isEditable()) {
            throw new BusinessException("Solo se pueden adjuntar archivos a borradores.");
        }
        AppProperties.Attachments limits = properties.getAttachments();
        List<Attachment> existing = attachments.findByMessageIdOrderByCreatedAtAsc(messageId);
        if (existing.size() >= limits.getMaxCount()) {
            throw new BusinessException("Máximo " + limits.getMaxCount() + " adjuntos por mensaje.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Selecciona un archivo.");
        }
        if (file.getSize() > limits.getMaxFileBytes()) {
            throw new BusinessException("Cada adjunto puede pesar como máximo "
                    + (limits.getMaxFileBytes() / (1024 * 1024)) + " MB.");
        }
        long total = existing.stream().mapToLong(Attachment::getSizeBytes).sum() + file.getSize();
        if (total > limits.getMaxTotalBytes()) {
            throw new BusinessException("El total de adjuntos supera " + (limits.getMaxTotalBytes() / (1024 * 1024)) + " MB.");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8192);
        }
        AttachmentValidator.Validated validated = AttachmentValidator.validate(file.getOriginalFilename(), head, file.getSize());

        String stored;
        try (InputStream in = file.getInputStream()) {
            stored = storage.store(AREA + "/" + ownerId, in);
        }
        Path path = storage.resolve(AREA + "/" + ownerId, stored);
        String sha256 = Hashing.sha256Hex(Files.readAllBytes(path));

        Attachment attachment = new Attachment();
        attachment.setMessageId(messageId);
        attachment.setOwnerId(ownerId);
        attachment.setOriginalName(validated.safeName());
        attachment.setStoredName(stored);
        attachment.setContentType(validated.contentType());
        attachment.setSizeBytes(file.getSize());
        attachment.setSha256(sha256);
        attachment = attachments.save(attachment);
        message.setUpdatedAt(java.time.Instant.now());
        messages.save(message);
        audit.record("ATTACHMENT_ADDED", "MESSAGE", String.valueOf(messageId), validated.safeName() + " (" + file.getSize() + " B)", ip);
        return attachment;
    }

    @Transactional
    public void remove(Long attachmentId, Long ownerId, String ip) {
        Attachment attachment = getOwned(attachmentId, ownerId);
        Message message = messages.findByIdAndOwnerId(attachment.getMessageId(), ownerId)
                .orElseThrow(() -> new NotFoundException("Mensaje no encontrado"));
        if (!message.getStatus().isEditable()) {
            throw new BusinessException("Solo se pueden quitar adjuntos de borradores.");
        }
        attachments.delete(attachment);
        storage.delete(AREA + "/" + ownerId, attachment.getStoredName());
        audit.record("ATTACHMENT_REMOVED", "MESSAGE", String.valueOf(message.getId()), attachment.getOriginalName(), ip);
    }

    /** Elimina los archivos físicos de un mensaje (la fila se borra en cascada). */
    public void deleteFilesFor(Long messageId, Long ownerId) {
        for (Attachment attachment : attachments.findByMessageIdOrderByCreatedAtAsc(messageId)) {
            storage.delete(AREA + "/" + ownerId, attachment.getStoredName());
        }
    }

    @Transactional
    public void copyAll(Long fromMessageId, Long toMessageId, Long ownerId) throws IOException {
        for (Attachment source : attachments.findByMessageIdOrderByCreatedAtAsc(fromMessageId)) {
            String stored;
            try (InputStream in = Files.newInputStream(pathOf(source))) {
                stored = storage.store(AREA + "/" + ownerId, in);
            }
            Attachment copy = new Attachment();
            copy.setMessageId(toMessageId);
            copy.setOwnerId(ownerId);
            copy.setOriginalName(source.getOriginalName());
            copy.setStoredName(stored);
            copy.setContentType(source.getContentType());
            copy.setSizeBytes(source.getSizeBytes());
            copy.setSha256(source.getSha256());
            attachments.save(copy);
        }
    }

    /** Tipos que el navegador puede mostrar de forma segura en descarga; el resto se sirve como binario. */
    public static String downloadContentType(Attachment attachment) {
        List<String> safe = Arrays.asList("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp",
                "text/plain", "text/csv");
        return safe.contains(attachment.getContentType()) ? attachment.getContentType() : "application/octet-stream";
    }
}
