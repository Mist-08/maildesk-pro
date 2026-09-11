package com.mycompany.maildesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.messages.AttachmentRepository;
import com.mycompany.maildesk.messages.Message;
import com.mycompany.maildesk.messages.MessageRepository;
import com.mycompany.maildesk.messages.MessageService;
import com.mycompany.maildesk.support.IntegrationTestBase;
import com.mycompany.maildesk.support.TestConfig;
import com.mycompany.maildesk.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

@Import(TestConfig.class)
class SecurityHardeningIT extends IntegrationTestBase {

    @Autowired private MessageService messages;
    @Autowired private MessageRepository messageRepository;
    @Autowired private AttachmentRepository attachmentRepository;

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        data.activeUser("ana@empresa.test");
        MockHttpSession ana = data.login(mockMvc, greenMail, "ana@empresa.test");
        mockMvc.perform(post("/contacts").session(ana).param("fullName", "X").param("email", "x@y.test"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/login").param("email", "ana@empresa.test").param("password", "x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").session(ana)).andExpect(status().isForbidden());
    }

    @Test
    void dangerousHtmlIsSanitizedOnSaveAndOnDisplay() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        String evil = "<p onclick=\"alert(1)\">Hola</p><script>alert('xss')</script><img src=\"x\" onerror=\"alert(2)\">"
                + "<a href=\"javascript:alert(3)\">enlace</a><iframe src=\"https://evil.test\"></iframe>";
        mockMvc.perform(post("/messages/" + draft.getId() + "/save").session(session).with(csrf())
                        .param("to", "a@b.test").param("subject", "Prueba").param("body", evil).param("contentType", "HTML"))
                .andExpect(status().is3xxRedirection());
        String stored = messageRepository.findById(draft.getId()).orElseThrow().getBodyHtml();
        assertThat(stored).doesNotContain("<script").doesNotContain("onerror").doesNotContain("onclick")
                .doesNotContain("javascript:").doesNotContain("<iframe").contains("Hola");
        String page = mockMvc.perform(get("/messages/" + draft.getId()).session(session)).andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("<script>alert").doesNotContain("onerror=");
    }

    @Test
    void invalidAttachmentsAreRejected() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        Message draft = messages.createDraft(ana.getId());
        String url = "/messages/" + draft.getId() + "/attachments";

        // Ejecutable
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "virus.exe", "application/octet-stream", "MZ...".getBytes()))
                .session(session).with(csrf())).andExpect(status().is3xxRedirection());
        // HTML disfrazado de PNG (firma binaria incorrecta)
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "foto.png", "image/png", "<html><script>x</script>".getBytes()))
                .session(session).with(csrf())).andExpect(status().is3xxRedirection());
        // Doble extensión / HTML
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "pagina.html", "text/html", "<html>".getBytes()))
                .session(session).with(csrf())).andExpect(status().is3xxRedirection());
        // Traversal en el nombre
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "../../etc/passwd.txt", "text/plain", "root".getBytes()))
                .session(session).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(attachmentRepository.findByMessageIdOrderByCreatedAtAsc(draft.getId()))
                .hasSize(1)
                .allSatisfy(a -> {
                    assertThat(a.getOriginalName()).isEqualTo("passwd.txt");
                    assertThat(a.getStoredName()).matches("[a-f0-9]{32}\\.bin");
                });
        // PDF válido
        mockMvc.perform(multipart(url).file(new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes()))
                .session(session).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(attachmentRepository.findByMessageIdOrderByCreatedAtAsc(draft.getId())).hasSize(2);

        // Descarga: como adjunto y sin sniffing.
        Long id = attachmentRepository.findByMessageIdOrderByCreatedAtAsc(draft.getId()).get(1).getId();
        mockMvc.perform(get("/attachments/" + id + "/download").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void headerInjectionInSubjectAndRecipientsIsRejected() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        greenMail.purgeEmailFromAllMailboxes();
        Message draft = messages.createDraft(ana.getId());
        mockMvc.perform(post("/messages/" + draft.getId() + "/save").session(session).with(csrf())
                        .param("to", "a@b.test").param("subject", "Hola\r\nBcc: victima@x.test").param("body", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("caracteres no permitidos")));
        mockMvc.perform(post("/messages/" + draft.getId() + "/send").session(session).with(csrf())
                        .param("to", "a@b.test\nCc: victima@x.test").param("subject", "Hola").param("body", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("caracteres no permitidos")));
        mockMvc.perform(post("/messages/" + draft.getId() + "/send").session(session).with(csrf())
                        .param("to", "\"Nombre\" <a@b.test>").param("subject", "Hola").param("body", "x"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no válida")));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        data.activeAdmin("admin@empresa.test");
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }
}
