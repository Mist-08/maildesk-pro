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
import com.mycompany.maildesk.user.UserRepository;
import com.mycompany.maildesk.user.UserRole;
import com.mycompany.maildesk.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestConfig.class)
class SetupAndInviteIT extends IntegrationTestBase {

    private static final String ADMIN_EMAIL = "admin@empresa.test";

    @Autowired
    private UserRepository users;

    @Test
    void initialAdminSetupRequiresSecretAndEmailVerificationAndIsSingleUse() throws Exception {
        mockMvc.perform(get("/login")).andExpect(redirectedUrl("/setup"));
        mockMvc.perform(get("/setup")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ADMIN_EMAIL)));

        mockMvc.perform(post("/setup").with(csrf()).param("setupSecret", "secreto-equivocado-123456")
                        .param("displayName", "Admin").param("password", "Clave-Admin-2026").param("passwordConfirm", "Clave-Admin-2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Secreto de instalación incorrecto")));
        assertThat(greenMail.getReceivedMessages()).isEmpty();

        MvcResult begin = mockMvc.perform(post("/setup").with(csrf()).param("setupSecret", "test-setup-secret-0123456789")
                        .param("displayName", "Admin").param("password", "Clave-Admin-2026").param("passwordConfirm", "Clave-Admin-2026"))
                .andExpect(redirectedUrl("/setup/verify")).andReturn();
        MockHttpSession session = (MockHttpSession) begin.getRequest().getSession(false);
        assertThat(users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);

        // Aún no hay acceso.
        mockMvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection());

        String code = lastCode();
        mockMvc.perform(post("/setup/verify").session(session).with(csrf()).param("code", code)).andExpect(redirectedUrl("/"));
        var admin = users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getEmailVerifiedAt()).isNotNull();
        mockMvc.perform(get("/admin/users").session(session)).andExpect(status().isOk());

        // Alta de un solo uso.
        mockMvc.perform(get("/setup")).andExpect(status().isNotFound());
        mockMvc.perform(post("/setup").with(csrf()).param("setupSecret", "test-setup-secret-0123456789")
                        .param("displayName", "Otro").param("password", "Clave-Admin-2026").param("passwordConfirm", "Clave-Admin-2026"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitationFlowCreatesVerifiedUserAndIsSingleUse() throws Exception {
        data.activeAdmin("admin@empresa.test");
        MockHttpSession adminSession = data.login(mockMvc, greenMail, "admin@empresa.test");
        greenMail.purgeEmailFromAllMailboxes();

        mockMvc.perform(post("/admin/users/invite").session(adminSession).with(csrf())
                        .param("email", "nuevo@empresa.test").param("role", "USER"))
                .andExpect(redirectedUrl("/admin/users"));
        String token = lastToken();

        mockMvc.perform(get("/invite/accept").param("token", token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nuevo@empresa.test")));
        mockMvc.perform(post("/invite/accept").with(csrf()).param("token", token).param("displayName", "Nuevo Usuario")
                        .param("password", com.mycompany.maildesk.support.TestData.PASSWORD).param("passwordConfirm", com.mycompany.maildesk.support.TestData.PASSWORD))
                .andExpect(redirectedUrl("/login"));
        var created = users.findByEmailIgnoreCase("nuevo@empresa.test").orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        assertThat(created.getEmailVerifiedAt()).isNotNull();

        // Un solo uso.
        mockMvc.perform(get("/invite/accept").param("token", token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no es válida")));

        // Un USER no puede invitar ni administrar.
        MockHttpSession userSession = data.login(mockMvc, greenMail, "nuevo@empresa.test");
        mockMvc.perform(get("/admin/users").session(userSession)).andExpect(status().isForbidden());
    }
}
