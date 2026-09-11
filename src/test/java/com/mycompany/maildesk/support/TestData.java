package com.mycompany.maildesk.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.mycompany.maildesk.user.User;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserService;
import com.mycompany.maildesk.user.UserStatus;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Utilidades de datos para pruebas: limpieza de tablas, usuarios activos y acceso completo en dos pasos. */
@TestComponent
public class TestData {

    public static final String PASSWORD = "Clave-Segura-2026";
    private static final Pattern CODE = Pattern.compile("\\b(\\d{8})\\b");

    private final JdbcTemplate jdbc;
    private final UserService users;

    public TestData(JdbcTemplate jdbc, UserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    public void reset() {
        for (String table : new String[]{"attachments", "messages", "contact_tags", "contacts", "tags", "mail_templates",
                "auth_challenges", "password_reset_tokens", "invitations", "rate_limit_events", "audit_log",
                "app_settings", "users"}) {
            jdbc.update("delete from " + table);
        }
    }

    public User activeUser(String email) {
        return users.create(email, "Usuario " + email.substring(0, email.indexOf('@')), PASSWORD, UserRole.USER, UserStatus.ACTIVE);
    }

    public User activeAdmin(String email) {
        return users.create(email, "Admin", PASSWORD, UserRole.ADMIN, UserStatus.ACTIVE);
    }

    /** Ejecuta el acceso completo (contraseña + código) y devuelve la sesión autenticada. */
    public MockHttpSession login(MockMvc mockMvc, GreenMailExtension greenMail, String email) throws Exception {
        int before = greenMail.getReceivedMessages().length;
        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/verify"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) step1.getRequest().getSession(false);
        MimeMessage[] messages = greenMail.getReceivedMessages();
        if (messages.length <= before) {
            throw new AssertionError("No se envió el código de verificación");
        }
        Matcher m = CODE.matcher(textOf(messages[messages.length - 1]));
        if (!m.find()) {
            throw new AssertionError("Correo sin código");
        }
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", m.group(1)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        return session;
    }

    public static String textOf(MimeMessage message) {
        try {
            Object content = message.getContent();
            if (content instanceof String s) {
                return s;
            }
            if (content instanceof MimeMultipart multipart) {
                StringBuilder sb = new StringBuilder();
                collect(multipart, sb);
                return sb.toString();
            }
            return String.valueOf(content);
        } catch (Exception e) {
            throw new AssertionError("No se pudo leer el correo", e);
        }
    }

    private static void collect(MimeMultipart multipart, StringBuilder sb) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            Object part = multipart.getBodyPart(i).getContent();
            if (part instanceof String s) {
                sb.append(s).append('\n');
            } else if (part instanceof MimeMultipart nested) {
                collect(nested, sb);
            }
        }
    }
}
