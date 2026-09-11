package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.auth.CurrentUser;
import com.mycompany.maildesk.common.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Descarga de adjuntos: solo el propietario, siempre como descarga (nunca se renderiza contenido activo). */
@Controller
public class AttachmentController {

    private final AttachmentService attachments;
    private final CurrentUser currentUser;

    public AttachmentController(AttachmentService attachments, CurrentUser currentUser) {
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Attachment attachment = attachments.getOwned(id, currentUser.id());
        Path path = attachments.pathOf(attachment);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Archivo no disponible");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(AttachmentService.downloadContentType(attachment)))
                .contentLength(attachment.getSizeBytes())
                .body(new FileSystemResource(path));
    }
}
