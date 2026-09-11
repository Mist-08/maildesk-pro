package com.mycompany.maildesk.support;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base para pruebas de integración: contexto Spring con perfil test (H2 en memoria) y GreenMail como
 * servidor SMTP aislado en 127.0.0.1:3025. Nunca se envía correo externo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @RegisterExtension
    protected static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    private static final Pattern CODE = Pattern.compile("\\b(\\d{8})\\b");
    private static final Pattern TOKEN_LINK = Pattern.compile("token=([A-Za-z0-9_\\-]+)");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestData data;

    @BeforeEach
    void resetDatabaseAndMailbox() throws Exception {
        data.reset();
        greenMail.purgeEmailFromAllMailboxes();
    }

    /** Extrae el código de 8 dígitos del último correo recibido por GreenMail. */
    protected String lastCode() {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        if (messages.length == 0) {
            throw new AssertionError("GreenMail no recibió ningún correo");
        }
        String body = TestData.textOf(messages[messages.length - 1]);
        Matcher m = CODE.matcher(body);
        if (!m.find()) {
            throw new AssertionError("El correo no contiene un código de 8 dígitos");
        }
        return m.group(1);
    }

    protected String lastToken() {
        MimeMessage[] messages = greenMail.getReceivedMessages();
        if (messages.length == 0) {
            throw new AssertionError("GreenMail no recibió ningún correo");
        }
        String body = TestData.textOf(messages[messages.length - 1]);
        Matcher m = TOKEN_LINK.matcher(body);
        if (!m.find()) {
            throw new AssertionError("El correo no contiene un enlace con token");
        }
        return m.group(1);
    }
}
