package com.mycompany.maildesk.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.support.IntegrationTestBase;
import com.mycompany.maildesk.support.TestConfig;
import com.mycompany.maildesk.user.User;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

@Import(TestConfig.class)
class OutboxIT extends IntegrationTestBase {

    @Autowired private MessageService messages;
    @Autowired private MessageRepository repository;
    @Autowired private AttachmentService attachments;
    @Autowired private OutboxProcessor outbox;

    private DraftForm form(String to, String subject, String body) {
        DraftForm f = new DraftForm();
        f.setTo(to);
        f.setSubject(subject);
        f.setBody(body);
        f.setContentType(ContentType.HTML);
        return f;
    }

    @Test
    void draftAutosavePersistsThroughJsonApi() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        mockMvc.perform(put("/api/drafts/" + draft.getId()).session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"cliente@x.test\",\"subject\":\"Cotización\",\"body\":\"<p>Hola</p>\",\"contentType\":\"HTML\",\"includeSignature\":true}"))
                .andExpect(status().isOk());
        Message saved = repository.findById(draft.getId()).orElseThrow();
        assertThat(saved.getSubject()).isEqualTo("Cotización");
        assertThat(saved.getToRecipients()).isEqualTo("cliente@x.test");
        assertThat(saved.getStatus()).isEqualTo(MessageStatus.DRAFT);
        mockMvc.perform(get("/messages/" + draft.getId() + "/edit").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cotización")));
    }

    @Test
    void queuedMessageIsAcceptedBySmtpWithAuthorizedSenderAndReplyTo() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        attachments.add(draft.getId(), ana.getId(), new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4 x".getBytes()), "127.0.0.1");
        DraftForm form = form("cliente@x.test, otro@x.test", "Cotización", "<p>Hola <b>cliente</b></p><script>alert(1)</script>");
        form.setSubmissionToken("token-doble-clic-0001");

        Message queued = messages.send(draft.getId(), ana.getId(), form, "127.0.0.1");
        assertThat(queued.getStatus()).isEqualTo(MessageStatus.QUEUED);

        // Doble clic con el mismo token: no se crea otro envío ni cambia el estado.
        Message again = messages.send(draft.getId(), ana.getId(), form, "127.0.0.1");
        assertThat(again.getStatus()).isEqualTo(MessageStatus.QUEUED);
        assertThat(repository.count()).isEqualTo(1);

        int claimed = outbox.runOnce();
        assertThat(claimed).isEqualTo(1);
        Message sent = repository.findById(draft.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(MessageStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
        assertThat(sent.getSmtpResponse()).startsWith("250");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(2); // uno por destinatario
        MimeMessage mime = received[0];
        jakarta.mail.internet.InternetAddress from = (jakarta.mail.internet.InternetAddress) mime.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("pruebas@maildesk.test");
        assertThat(from.getPersonal()).contains("Usuario ana");
        assertThat(mime.getReplyTo()[0].toString()).contains("ana@empresa.test");
        assertThat(mime.getSubject()).isEqualTo("Cotización");
        String text = com.mycompany.maildesk.support.TestData.textOf(mime);
        assertThat(text).contains("Hola").doesNotContain("<script");
        assertThat(mime.getContentType()).startsWith("multipart/");

        // Un segundo ciclo no reenvía nada.
        assertThat(outbox.runOnce()).isZero();
    }

    @Test
    void smtpFailuresAreClassifiedAndRetriedWithinLimit() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        messages.send(draft.getId(), ana.getId(), form("cliente@x.test", "Prueba", "<p>x</p>"), "127.0.0.1");

        greenMail.stop();
        try {
            outbox.runOnce();
            Message afterFirst = repository.findById(draft.getId()).orElseThrow();
            assertThat(afterFirst.getStatus()).isEqualTo(MessageStatus.QUEUED); // reintentable (conexión rechazada)
            assertThat(afterFirst.getAttempts()).isEqualTo(1);
            assertThat(afterFirst.getLastError()).isNotBlank();

            outbox.runOnce();
            Message afterSecond = repository.findById(draft.getId()).orElseThrow();
            assertThat(afterSecond.getStatus()).isEqualTo(MessageStatus.FAILED); // máximo 2 intentos en pruebas
            assertThat(afterSecond.getFailedAt()).isNotNull();
        } finally {
            greenMail.start();
        }

        // Reintento manual vuelve a encolar y con el servidor disponible se acepta.
        assertThat(messages.retry(draft.getId(), ana.getId(), "127.0.0.1")).isTrue();
        outbox.runOnce();
        assertThat(repository.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(MessageStatus.SENT);
    }

    @Test
    void sendRequiresRecipientsAndSubjectAndRespectsQuota() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        Message empty = messages.createDraft(ana.getId());
        assertThatThrownBy(() -> messages.send(empty.getId(), ana.getId(), form("", "Asunto", "x"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("destinatario");
        assertThatThrownBy(() -> messages.send(empty.getId(), ana.getId(), form("a@b.test", "", "x"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("asunto");
        assertThatThrownBy(() -> messages.send(empty.getId(), ana.getId(), form("no-es-correo", "Asunto", "x"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no válida");

        for (int i = 0; i < 5; i++) {
            Message m = messages.createDraft(ana.getId());
            messages.send(m.getId(), ana.getId(), form("a@b.test", "Asunto " + i, "x"), "127.0.0.1");
        }
        Message sixth = messages.createDraft(ana.getId());
        assertThatThrownBy(() -> messages.send(sixth.getId(), ana.getId(), form("a@b.test", "Asunto 6", "x"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cuota diaria");
    }

    @Test
    void queuedMessageCanBeCancelledAndCannotBeEdited() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        messages.send(draft.getId(), ana.getId(), form("a@b.test", "Asunto", "x"), "127.0.0.1");
        mockMvc.perform(get("/messages/" + draft.getId() + "/edit").session(session)).andExpect(redirectedUrl("/messages/" + draft.getId()));
        mockMvc.perform(post("/messages/" + draft.getId() + "/cancel").session(session).with(csrf())).andExpect(redirectedUrl("/messages/" + draft.getId()));
        assertThat(repository.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(MessageStatus.CANCELLED);
        assertThat(outbox.runOnce()).isZero();
    }

    @Test
    void staleSendingMessagesBecomeUncertainInsteadOfBeingResent() {
        User ana = data.activeUser("ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        messages.send(draft.getId(), ana.getId(), form("a@b.test", "Asunto", "x"), "127.0.0.1");
        Message m = repository.findById(draft.getId()).orElseThrow();
        m.setStatus(MessageStatus.SENDING);
        m.setLockedAt(java.time.Instant.now().minusSeconds(3600));
        repository.saveAndFlush(m);
        outbox.runOnce();
        assertThat(repository.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(MessageStatus.UNCERTAIN);
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
