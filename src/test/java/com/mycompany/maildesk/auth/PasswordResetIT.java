package com.mycompany.maildesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.support.IntegrationTestBase;
import com.mycompany.maildesk.support.TestConfig;
import com.mycompany.maildesk.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;

@Import(TestConfig.class)
class PasswordResetIT extends IntegrationTestBase {

    @Test
    void resetRevokesSessionsIsSingleUseAndKeepsSecondStep() throws Exception {
        data.activeUser("ana@empresa.test");
        MockHttpSession oldSession = data.login(mockMvc, greenMail, "ana@empresa.test");
        mockMvc.perform(get("/").session(oldSession)).andExpect(status().isOk());
        greenMail.purgeEmailFromAllMailboxes();

        // Solicitud: respuesta genérica, correo con enlace.
        mockMvc.perform(post("/password/forgot").with(csrf()).param("email", "ana@empresa.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Si el correo corresponde")));
        String token = lastToken();
        assertThat(token.length()).isGreaterThanOrEqualTo(40);

        mockMvc.perform(get("/password/reset").param("token", token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nueva contraseña")));

        String newPassword = "Nueva-Clave-2026!";
        mockMvc.perform(post("/password/reset").with(csrf()).param("token", token)
                        .param("password", newPassword).param("passwordConfirm", newPassword))
                .andExpect(redirectedUrl("/login"));

        // La sesión anterior queda revocada.
        mockMvc.perform(get("/").session(oldSession)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?revoked"));

        // El token es de un solo uso.
        mockMvc.perform(get("/password/reset").param("token", token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no es válido")));
        mockMvc.perform(post("/password/reset").with(csrf()).param("token", token)
                        .param("password", newPassword).param("passwordConfirm", newPassword))
                .andExpect(status().isOk());

        // La contraseña anterior deja de funcionar y la nueva exige el segundo paso.
        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD))
                .andExpect(status().isOk());
        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", newPassword))
                .andExpect(redirectedUrl("/login/verify"));
    }

    @Test
    void unknownEmailGetsSameResponseAndNoMail() throws Exception {
        mockMvc.perform(post("/password/forgot").with(csrf()).param("email", "nadie@empresa.test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Si el correo corresponde")));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/password/reset").param("token", "token-invalido-pero-largo-123456789"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no es válido")));
    }
}
