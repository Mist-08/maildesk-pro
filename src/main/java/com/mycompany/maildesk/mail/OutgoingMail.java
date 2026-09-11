package com.mycompany.maildesk.mail;

import java.nio.file.Path;
import java.util.List;

/**
 * Correo listo para transportar. El remitente real (From) siempre es la cuenta autorizada configurada
 * en {@code MAIL_FROM}; el usuario que redacta aparece en el nombre visible y en Reply-To.
 */
public record OutgoingMail(
        String fromDisplayName,
        String replyTo,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String textBody,
        String htmlBody,
        List<MailAttachment> attachments) {

    public record MailAttachment(String fileName, String contentType, Path path) {}

    public static OutgoingMail system(String to, String subject, String textBody, String htmlBody) {
        return new OutgoingMail(null, null, List.of(to), List.of(), List.of(), subject, textBody, htmlBody, List.of());
    }

    public int recipientCount() {
        return to.size() + cc.size() + bcc.size();
    }
}
