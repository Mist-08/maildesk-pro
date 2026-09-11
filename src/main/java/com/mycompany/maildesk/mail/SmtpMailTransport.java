package com.mycompany.maildesk.mail;

import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.SendOutcome.FailureKind;
import jakarta.activation.FileDataSource;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Transporte SMTP con Jakarta Mail / Eclipse Angus. Separa explícitamente la fase de conexión de la
 * fase de transmisión para clasificar los fallos con honestidad.
 */
@Component
public class SmtpMailTransport implements MailTransport {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailTransport.class);

    private final JavaMailSenderImpl mailSender;
    private final AppProperties properties;

    public SmtpMailTransport(JavaMailSenderImpl mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return mailSender.getHost() != null && !mailSender.getHost().isBlank()
                && properties.getMail().getFrom() != null && !properties.getMail().getFrom().isBlank();
    }

    @Override
    public SendOutcome send(OutgoingMail mail) {
        if (!isConfigured()) {
            return SendOutcome.failure(FailureKind.CONFIGURATION,
                    "Servidor SMTP o remitente (MAIL_HOST / MAIL_FROM) sin configurar.");
        }
        MimeMessage message;
        try {
            message = build(mail);
        } catch (MessagingException | UnsupportedEncodingException | IllegalArgumentException e) {
            return SendOutcome.failure(FailureKind.REJECTED, "Mensaje no válido: " + SendFailureClassifier.describe(e));
        }

        Session session = mailSender.getSession();
        Transport transport;
        try {
            transport = session.getTransport(mailSender.getProtocol());
        } catch (MessagingException e) {
            return SendOutcome.failure(FailureKind.CONFIGURATION, SendFailureClassifier.describe(e));
        }
        try {
            try {
                transport.connect(mailSender.getHost(), mailSender.getPort(), blankToNull(mailSender.getUsername()),
                        blankToNull(mailSender.getPassword()));
            } catch (MessagingException e) {
                FailureKind kind = SendFailureClassifier.classifyConnectFailure(e);
                log.warn("Fallo de conexión SMTP ({}): {}", kind, SendFailureClassifier.describe(e));
                return SendOutcome.failure(kind, SendFailureClassifier.describe(e));
            }
            try {
                Address[] recipients = message.getAllRecipients();
                transport.sendMessage(message, recipients);
                String response = transport instanceof SMTPTransport smtp ? smtp.getLastServerResponse() : null;
                return SendOutcome.accepted(trim(response));
            } catch (MessagingException e) {
                FailureKind kind = SendFailureClassifier.classifySendFailure(e);
                log.warn("Fallo al transmitir mensaje ({}): {}", kind, SendFailureClassifier.describe(e));
                return SendOutcome.failure(kind, SendFailureClassifier.describe(e));
            }
        } finally {
            try {
                transport.close();
            } catch (MessagingException ignored) {
                // La conexión ya no es útil.
            }
        }
    }

    private MimeMessage build(OutgoingMail mail) throws MessagingException, UnsupportedEncodingException {
        boolean multipart = !mail.attachments().isEmpty() || (mail.htmlBody() != null && !mail.htmlBody().isBlank());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, multipart, StandardCharsets.UTF_8.name());

        String fromAddress = properties.getMail().getFrom();
        String fromName = properties.getMail().getFromName();
        if (mail.fromDisplayName() != null && !mail.fromDisplayName().isBlank()) {
            fromName = mail.fromDisplayName() + " · " + fromName;
        }
        MailAddressValidator.rejectHeaderInjection(fromName, "remitente");
        helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
        if (mail.replyTo() != null && !mail.replyTo().isBlank()) {
            helper.setReplyTo(MailAddressValidator.normalizeSingle(mail.replyTo(), "responder a"));
        }
        for (String to : mail.to()) {
            helper.addTo(MailAddressValidator.normalizeSingle(to, "Para"));
        }
        for (String cc : mail.cc()) {
            helper.addCc(MailAddressValidator.normalizeSingle(cc, "CC"));
        }
        for (String bcc : mail.bcc()) {
            helper.addBcc(MailAddressValidator.normalizeSingle(bcc, "CCO"));
        }
        if (mail.recipientCount() == 0) {
            throw new IllegalArgumentException("Sin destinatarios");
        }
        MailAddressValidator.rejectHeaderInjection(mail.subject(), "asunto");
        helper.setSubject(mail.subject() == null ? "" : mail.subject());
        if (mail.htmlBody() != null && !mail.htmlBody().isBlank()) {
            helper.setText(mail.textBody() == null ? "" : mail.textBody(), mail.htmlBody());
        } else {
            helper.setText(mail.textBody() == null ? "" : mail.textBody(), false);
        }
        for (OutgoingMail.MailAttachment attachment : mail.attachments()) {
            FileDataSource source = new FileDataSource(attachment.path().toFile());
            source.setFileTypeMap(new jakarta.activation.FileTypeMap() {
                @Override public String getContentType(java.io.File file) { return attachment.contentType(); }
                @Override public String getContentType(String filename) { return attachment.contentType(); }
            });
            helper.addAttachment(attachment.fileName(), source);
        }
        message.setHeader("X-Mailer", "MailDesk Pro");
        message.setRecipients(Message.RecipientType.TO, message.getRecipients(Message.RecipientType.TO));
        return message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String trim(String response) {
        if (response == null) {
            return null;
        }
        String cleaned = response.replaceAll("[\\r\\n]+", " ").strip();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }
}
