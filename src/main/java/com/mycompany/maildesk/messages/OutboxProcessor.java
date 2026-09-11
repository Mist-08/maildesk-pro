package com.mycompany.maildesk.messages;

import com.mycompany.maildesk.audit.AuditService;
import com.mycompany.maildesk.common.HtmlSanitizer;
import com.mycompany.maildesk.config.AppProperties;
import com.mycompany.maildesk.mail.MailTransport;
import com.mycompany.maildesk.mail.OutgoingMail;
import com.mycompany.maildesk.mail.SendOutcome;
import com.mycompany.maildesk.settings.SettingsService;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRepository;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesador de la cola de salida persistente. Reclama mensajes de forma atómica, los envía con
 * concurrencia limitada y clasifica el resultado: aceptado, reintentable, fallido o incierto.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final MessageRepository messages;
    private final AttachmentService attachments;
    private final UserRepository users;
    private final MailTransport transport;
    private final SettingsService settings;
    private final HtmlSanitizer sanitizer;
    private final AppProperties properties;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxProcessor(MessageRepository messages, AttachmentService attachments, UserRepository users,
                           MailTransport transport, SettingsService settings, HtmlSanitizer sanitizer,
                           AppProperties properties, AuditService audit, PlatformTransactionManager txManager) {
        this.messages = messages;
        this.attachments = attachments;
        this.users = users;
        this.transport = transport;
        this.settings = settings;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.workers = Executors.newFixedThreadPool(Math.max(1, properties.getMail().getConcurrency()), r -> {
            Thread t = new Thread(r, "outbox-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:5000}", initialDelay = 10_000)
    public void poll() {
        if (properties.getOutbox().isSchedulerEnabled()) {
            runOnce();
        }
    }

    /** Procesa un lote y espera a que termine. Devuelve cuántos mensajes se reclamaron. */
    public int runOnce() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            Instant now = Instant.now();
            tx.executeWithoutResult(s -> messages.markStaleSendingAsUncertain(now.minus(Duration.ofMinutes(10)), now));
            List<Long> due = messages.findDueQueuedIds(now, PageRequest.of(0, properties.getOutbox().getBatchSize()));
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int claimed = 0;
            for (Long id : due) {
                Integer ok = tx.execute(s -> messages.claim(id, Instant.now()));
                if (ok != null && ok == 1) {
                    claimed++;
                    futures.add(CompletableFuture.runAsync(() -> process(id), workers));
                }
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return claimed;
        } catch (RuntimeException e) {
            log.error("Error procesando la cola de salida", e);
            return 0;
        } finally {
            running.set(false);
        }
    }

    void process(Long id) {
        Optional<Message> loaded = messages.findById(id);
        if (loaded.isEmpty()) {
            return;
        }
        Message message = loaded.get();
        Optional<User> owner = users.findById(message.getOwnerId());
        if (owner.isEmpty()) {
            fail(message, "Propietario inexistente", false);
            return;
        }
        OutgoingMail mail;
        try {
            mail = build(message, owner.get());
        } catch (RuntimeException e) {
            fail(message, "No se pudo construir el mensaje: " + e.getMessage(), false);
            return;
        }
        SendOutcome outcome = transport.send(mail);
        Instant now = Instant.now();
        switch (outcome.kind()) {
            case NONE -> tx.executeWithoutResult(s -> messages.findById(id).ifPresent(m -> {
                m.setStatus(MessageStatus.SENT);
                m.setSentAt(now);
                m.setSmtpResponse(outcome.serverResponse());
                m.setLastError(null);
                m.setLockedAt(null);
                m.setAttempts(m.getAttempts() + 1);
                messages.save(m);
                audit.recordAs(m.getOwnerId(), owner.get().getEmail(), "MESSAGE_ACCEPTED_BY_SMTP", "MESSAGE",
                        String.valueOf(m.getId()), outcome.serverResponse(), null);
            }));
            case TRANSIENT -> tx.executeWithoutResult(s -> messages.findById(id).ifPresent(m -> {
                int attempts = m.getAttempts() + 1;
                m.setAttempts(attempts);
                m.setLastError(outcome.error());
                m.setLockedAt(null);
                if (attempts < properties.getMail().getMaxAttempts()) {
                    m.setStatus(MessageStatus.QUEUED);
                    m.setNextAttemptAt(now.plusSeconds(properties.getMail().getRetryBackoffSeconds() * attempts));
                } else {
                    m.setStatus(MessageStatus.FAILED);
                    m.setFailedAt(now);
                    audit.recordAs(m.getOwnerId(), owner.get().getEmail(), "MESSAGE_FAILED", "MESSAGE",
                            String.valueOf(m.getId()), outcome.error(), null);
                }
                messages.save(m);
            }));
            case UNCERTAIN -> tx.executeWithoutResult(s -> messages.findById(id).ifPresent(m -> {
                m.setStatus(MessageStatus.UNCERTAIN);
                m.setAttempts(m.getAttempts() + 1);
                m.setLastError(outcome.error());
                m.setLockedAt(null);
                messages.save(m);
                audit.recordAs(m.getOwnerId(), owner.get().getEmail(), "MESSAGE_UNCERTAIN", "MESSAGE",
                        String.valueOf(m.getId()), outcome.error(), null);
            }));
            default -> fail(message, outcome.error(), true);
        }
    }

    private void fail(Message message, String error, boolean fromTransport) {
        tx.executeWithoutResult(s -> messages.findById(message.getId()).ifPresent(m -> {
            m.setStatus(MessageStatus.FAILED);
            m.setFailedAt(Instant.now());
            m.setAttempts(m.getAttempts() + 1);
            m.setLastError(error);
            m.setLockedAt(null);
            messages.save(m);
            audit.recordAs(m.getOwnerId(), null, "MESSAGE_FAILED", "MESSAGE", String.valueOf(m.getId()), error, null);
        }));
    }

    private OutgoingMail build(Message message, User owner) {
        String signature = owner.getSignatureHtml();
        if (signature == null || signature.isBlank()) {
            signature = settings.getDefaultSignature();
        }
        String html;
        String text;
        if (message.getContentType() == ContentType.HTML) {
            String body = sanitizer.sanitize(message.getBodyHtml());
            if (message.isIncludeSignature() && !signature.isBlank()) {
                body = body + "<br><br>-- <br>" + sanitizer.sanitize(signature);
            }
            html = "<!doctype html><html><body>" + body + "</body></html>";
            text = sanitizer.htmlToText(body);
        } else {
            text = message.getBodyHtml() == null ? "" : message.getBodyHtml();
            if (message.isIncludeSignature() && !signature.isBlank()) {
                text = text + "\n\n-- \n" + sanitizer.htmlToText(signature);
            }
            html = null;
        }
        List<OutgoingMail.MailAttachment> files = attachments.listFor(message.getId()).stream()
                .map(a -> new OutgoingMail.MailAttachment(a.getOriginalName(), a.getContentType(), attachments.pathOf(a)))
                .toList();
        return new OutgoingMail(owner.getDisplayName(), owner.getEmail(), message.toList(), message.ccList(),
                message.bccList(), message.getSubject(), text, html, files);
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
        try {
            workers.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
