package com.mycompany.maildesk.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.contacts.Contact;
import com.mycompany.maildesk.contacts.ContactService;
import com.mycompany.maildesk.messages.Attachment;
import com.mycompany.maildesk.messages.AttachmentService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

@Import(TestConfig.class)
class AuthorizationIT extends IntegrationTestBase {

    @Autowired private MessageService messages;
    @Autowired private AttachmentService attachments;
    @Autowired private ContactService contacts;
    @Autowired private TemplateService templates;

    @Test
    void usersCannotAccessResourcesOfOtherUsersEvenAsAdmin() throws Exception {
        User ana = data.activeUser("ana@empresa.test");
        data.activeUser("beto@empresa.test");
        data.activeAdmin("admin@empresa.test");

        Message draft = messages.createDraft(ana.getId());
        Attachment attachment = attachments.add(draft.getId(), ana.getId(),
                new MockMultipartFile("file", "nota.txt", "text/plain", "hola".getBytes()), "127.0.0.1");
        Contact contact = contacts.save(ana.getId(), null, "Cliente", "cliente@x.test", null, null, null, List.of(), "127.0.0.1");
        MailTemplate template = templates.save(ana.getId(), null, "Plantilla", "Asunto", "<p>Hola</p>", "127.0.0.1");

        MockHttpSession beto = data.login(mockMvc, greenMail, "beto@empresa.test");
        MockHttpSession admin = data.login(mockMvc, greenMail, "admin@empresa.test");

        for (MockHttpSession other : List.of(beto, admin)) {
            mockMvc.perform(get("/messages/" + draft.getId()).session(other)).andExpect(status().isNotFound());
            mockMvc.perform(get("/messages/" + draft.getId() + "/edit").session(other)).andExpect(status().isNotFound());
            mockMvc.perform(post("/messages/" + draft.getId() + "/send").session(other).with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(put("/api/drafts/" + draft.getId()).session(other).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"robado\"}")).andExpect(status().isNotFound());
            mockMvc.perform(get("/attachments/" + attachment.getId() + "/download").session(other)).andExpect(status().isNotFound());
            mockMvc.perform(multipart("/messages/" + draft.getId() + "/attachments").file(
                    new MockMultipartFile("file", "otro.txt", "text/plain", "x".getBytes())).session(other).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/contacts/" + contact.getId() + "/edit").session(other)).andExpect(status().isNotFound());
            mockMvc.perform(post("/contacts/" + contact.getId() + "/delete").session(other).with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(get("/templates/" + template.getId() + "/edit").session(other)).andExpect(status().isNotFound());
        }
        // El propietario sí accede.
        MockHttpSession anaSession = data.login(mockMvc, greenMail, "ana@empresa.test");
        mockMvc.perform(get("/messages/" + draft.getId() + "/edit").session(anaSession)).andExpect(status().isOk());
        mockMvc.perform(get("/attachments/" + attachment.getId() + "/download").session(anaSession)).andExpect(status().isOk());
    }

    @Test
    void adminRoutesRequireAdminRole() throws Exception {
        data.activeUser("ana@empresa.test");
        MockHttpSession ana = data.login(mockMvc, greenMail, "ana@empresa.test");
        mockMvc.perform(get("/admin/users").session(ana)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/settings").session(ana)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit").session(ana)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/invite").session(ana).with(csrf()).param("email", "x@y.test")).andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/messages")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/account")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/attachments/1/download")).andExpect(status().is3xxRedirection());
    }
}
