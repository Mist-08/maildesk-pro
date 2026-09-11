package com.mycompany.maildesk.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.contacts.Contact;
import com.mycompany.maildesk.contacts.ContactService;
import com.mycompany.maildesk.messages.Message;
import com.mycompany.maildesk.messages.MessageService;
import com.mycompany.maildesk.support.IntegrationTestBase;
import com.mycompany.maildesk.support.TestConfig;
import com.mycompany.maildesk.templates.MailTemplate;
import com.mycompany.maildesk.templates.TemplateService;
import com.mycompany.maildesk.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

/** Renderiza todas las vistas principales para detectar errores de plantilla. */
@Import(TestConfig.class)
class PagesSmokeIT extends IntegrationTestBase {

    @Autowired private MessageService messages;
    @Autowired private ContactService contacts;
    @Autowired private TemplateService templates;

    @Test
    void allMainPagesRenderForAdmin() throws Exception {
        User admin = data.activeAdmin("admin@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "admin@empresa.test");
        Contact contact = contacts.save(admin.getId(), null, "Ana Cliente", "ana@cliente.test", "ACME", "555", "nota", List.of(), "127.0.0.1");
        contacts.createTag(admin.getId(), "VIP", "#ff0000");
        MailTemplate template = templates.save(admin.getId(), null, "Bienvenida", "Hola {{nombre}}", "<p>Gracias {{nombre}} de {{empresa}}</p>", "127.0.0.1");
        Message draft = messages.createDraft(admin.getId());

        for (String url : List.of("/", "/messages", "/messages?box=drafts&q=x", "/messages?box=outbox", "/messages?box=sent",
                "/messages?box=failed", "/messages/" + draft.getId() + "/edit", "/messages/" + draft.getId(),
                "/contacts", "/contacts?q=ana", "/contacts/new", "/contacts/" + contact.getId() + "/edit",
                "/templates", "/templates/new", "/templates/" + template.getId() + "/edit",
                "/account", "/admin/users", "/admin/settings", "/admin/audit", "/admin/audit?q=LOGIN")) {
            mockMvc.perform(get(url).session(session)).andExpect(status().isOk());
        }

        // Aplicar plantilla con contacto y ver el borrador resultante.
        mockMvc.perform(post("/messages/" + draft.getId() + "/apply-template").session(session).with(csrf())
                        .param("templateId", String.valueOf(template.getId())).param("contactId", String.valueOf(contact.getId())))
                .andExpect(redirectedUrl("/messages/" + draft.getId() + "/edit"));
        mockMvc.perform(get("/messages/" + draft.getId() + "/edit").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hola Ana Cliente")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ana@cliente.test")));

        // Redactar desde contacto y desde plantilla.
        mockMvc.perform(get("/messages/new?contactId=" + contact.getId()).session(session)).andExpect(redirectedUrlPattern("/messages/*/edit"));
        mockMvc.perform(get("/messages/new?templateId=" + template.getId()).session(session)).andExpect(redirectedUrlPattern("/messages/*/edit"));

        // Personalización: nombre, color, firma y logotipo.
        mockMvc.perform(post("/admin/settings/branding").session(session).with(csrf())
                        .param("businessName", "Mi Negocio SA").param("accentColor", "#0f766e").param("defaultSignature", "<p>Firma <b>oficial</b></p>"))
                .andExpect(redirectedUrl("/admin/settings"));
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        mockMvc.perform(multipart("/admin/settings/logo").file(new MockMultipartFile("logo", "logo.png", "image/png", png))
                .session(session).with(csrf())).andExpect(redirectedUrl("/admin/settings"));
        mockMvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mi Negocio SA")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("--accent:#0f766e")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/branding/logo")));
        mockMvc.perform(get("/branding/logo")).andExpect(status().isOk());

        // Cuenta: perfil y firma.
        mockMvc.perform(post("/account/profile").session(session).with(csrf()).param("displayName", "Admin Renombrado"))
                .andExpect(redirectedUrl("/account"));
        mockMvc.perform(post("/account/signature").session(session).with(csrf()).param("signatureHtml", "<p>Mi firma</p><script>x</script>"))
                .andExpect(redirectedUrl("/account"));
        mockMvc.perform(get("/account").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Admin Renombrado")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>x"))));

        // Páginas de error personalizadas.
        mockMvc.perform(get("/messages/999999").session(session)).andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No encontrado")));
        mockMvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/"));
    }

    @Test
    void emailChangeRequiresReauthenticationAndVerificationOfNewAddress() throws Exception {
        data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        greenMail.purgeEmailFromAllMailboxes();
        mockMvc.perform(post("/account/email/start").session(session).with(csrf())
                        .param("currentPassword", "incorrecta-000").param("newEmail", "nueva@empresa.test"))
                .andExpect(redirectedUrl("/account"));
        org.assertj.core.api.Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();

        mockMvc.perform(post("/account/email/start").session(session).with(csrf())
                        .param("currentPassword", com.mycompany.maildesk.support.TestData.PASSWORD).param("newEmail", "nueva@empresa.test"))
                .andExpect(redirectedUrl("/account/email/verify"));
        jakarta.mail.internet.MimeMessage mail = greenMail.getReceivedMessages()[0];
        org.assertj.core.api.Assertions.assertThat(mail.getAllRecipients()[0].toString()).isEqualTo("nueva@empresa.test");
        String code = lastCode();
        mockMvc.perform(get("/account/email/verify").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/account/email/verify").session(session).with(csrf()).param("code", code)).andExpect(redirectedUrl("/account"));
        mockMvc.perform(get("/account").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nueva@empresa.test")));
    }
}
