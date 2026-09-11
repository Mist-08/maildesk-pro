package com.mycompany.maildesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mycompany.maildesk.common.BusinessException;
import com.mycompany.maildesk.support.IntegrationTestBase;
import com.mycompany.maildesk.support.TestConfig;
import com.mycompany.maildesk.support.TestData;
import com.mycompany.maildesk.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestConfig.class)
class TwoStepLoginIT extends IntegrationTestBase {

    @Autowired
    private ChallengeService challenges;
    @Autowired
    private AuthChallengeRepository challengeRepository;

    @Test
    void wrongPasswordIsRejectedWithoutSendingCode() throws Exception {
        data.activeUser("ana@empresa.test");
        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", "incorrecta-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Correo o contraseña incorrectos")));
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void protectedRoutesAreBlockedUntilSecondStepCompletes() throws Exception {
        data.activeUser("ana@empresa.test");
        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD))
                .andExpect(redirectedUrl("/login/verify")).andReturn();
        MockHttpSession pending = (MockHttpSession) step1.getRequest().getSession(false);

        mockMvc.perform(get("/").session(pending)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/messages").session(pending)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/contacts").session(pending)).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/users").session(pending)).andExpect(status().is3xxRedirection());
        mockMvc.perform(put("/api/drafts/1").session(pending).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection());
        // La página de verificación sí es accesible.
        mockMvc.perform(get("/login/verify").session(pending)).andExpect(status().isOk());
    }

    @Test
    void wrongCodeThenCorrectCodeGrantsAccessAndRotatesSession() throws Exception {
        data.activeUser("ana@empresa.test");
        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) step1.getRequest().getSession(false);
        String idBefore = session.getId();
        String code = lastCode();
        String wrong = code.charAt(0) == '1' ? "2" + code.substring(1) : "1" + code.substring(1);

        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", wrong))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Código incorrecto")));
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/"));
        assertThat(session.getId()).isNotEqualTo(idBefore);
        mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/messages").session(session)).andExpect(status().isOk());
    }

    @Test
    void codeCannotBeReused() throws Exception {
        User user = data.activeUser("ana@empresa.test");
        PendingChallenge pending = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, "127.0.0.1", "prueba");
        String code = lastCode();
        assertThat(challenges.verify(pending, code).success()).isTrue();
        assertThat(challenges.verify(pending, code).outcome()).isEqualTo(ChallengeService.Outcome.EXPIRED_OR_USED);
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        User user = data.activeUser("ana@empresa.test");
        PendingChallenge pending = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, "127.0.0.1", "prueba");
        String code = lastCode();
        AuthChallenge challenge = challengeRepository.findById(pending.challengeId()).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        challengeRepository.saveAndFlush(challenge);
        assertThat(challenges.verify(pending, code).outcome()).isEqualTo(ChallengeService.Outcome.EXPIRED_OR_USED);
    }

    @Test
    void resendInvalidatesPreviousCodeAndRespectsInterval() throws Exception {
        data.activeUser("ana@empresa.test");
        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) step1.getRequest().getSession(false);
        String firstCode = lastCode();

        // Antes de 60 s el reenvío se rechaza.
        mockMvc.perform(post("/login/resend").session(session).with(csrf())).andExpect(redirectedUrl("/login/verify"));
        assertThat(greenMail.getReceivedMessages()).hasSize(1);

        // Simulamos que pasaron 61 s.
        PendingChallenge pending = (PendingChallenge) session.getAttribute(PendingChallenge.LOGIN_KEY);
        AuthChallenge challenge = challengeRepository.findById(pending.challengeId()).orElseThrow();
        challenge.setCreatedAt(Instant.now().minusSeconds(61));
        challengeRepository.saveAndFlush(challenge);

        mockMvc.perform(post("/login/resend").session(session).with(csrf())).andExpect(redirectedUrl("/login/verify"));
        assertThat(greenMail.getReceivedMessages()).hasSize(2);
        String secondCode = lastCode();

        // El código anterior ya no sirve (aunque coincida numéricamente con el desafío antiguo).
        PendingChallenge renewed = (PendingChallenge) session.getAttribute(PendingChallenge.LOGIN_KEY);
        assertThat(renewed.challengeId()).isNotEqualTo(pending.challengeId());
        assertThat(challengeRepository.findById(pending.challengeId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(challenges.verify(pending, firstCode).outcome()).isEqualTo(ChallengeService.Outcome.EXPIRED_OR_USED);

        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", secondCode)).andExpect(redirectedUrl("/"));
    }

    @Test
    void attemptsAreLimitedPerChallenge() throws Exception {
        data.activeUser("ana@empresa.test");
        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) step1.getRequest().getSession(false);
        String code = lastCode();
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", "00000000"))
                    .andExpect(status().isOk());
        }
        // Quinto intento fallido: el desafío queda agotado.
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", "00000000"))
                .andExpect(redirectedUrl("/login"));
        // El código correcto ya no sirve.
        mockMvc.perform(post("/login/verify").session(session).with(csrf()).param("code", code))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void requestsAreLimitedPerAccountPerHour() {
        User user = data.activeUser("ana@empresa.test");
        for (int i = 0; i < 5; i++) {
            PendingChallenge pending = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, "10.0.0." + i, "prueba");
            AuthChallenge c = challengeRepository.findById(pending.challengeId()).orElseThrow();
            c.setCreatedAt(Instant.now().minusSeconds(120));
            challengeRepository.saveAndFlush(c);
        }
        assertThatThrownBy(() -> challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, "10.0.0.9", "prueba"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("máximo de códigos por hora");
        assertThat(greenMail.getReceivedMessages()).hasSize(5);
    }

    @Test
    void requestsAreLimitedPerIp() {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            users.add(data.activeUser("u" + i + "@empresa.test"));
        }
        for (int i = 0; i < 20; i++) {
            challenges.issue(users.get(i), ChallengePurpose.LOGIN, users.get(i).getEmail(), null, "203.0.113.5", "prueba");
        }
        assertThatThrownBy(() -> challenges.issue(users.get(20), ChallengePurpose.LOGIN, users.get(20).getEmail(), null, "203.0.113.5", "prueba"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Demasiadas solicitudes");
    }

    @Test
    void concurrentVerificationsConsumeTheCodeExactlyOnce() throws Exception {
        User user = data.activeUser("ana@empresa.test");
        PendingChallenge pending = challenges.issue(user, ChallengePurpose.LOGIN, user.getEmail(), null, "127.0.0.1", "prueba");
        String code = lastCode();
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChallengeService.Verification>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return challenges.verify(pending, code);
            }));
        }
        start.countDown();
        long successes = 0;
        for (Future<ChallengeService.Verification> f : results) {
            if (f.get().success()) {
                successes++;
            }
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(1);
    }

    @Test
    void logoutEndsSessionAndCancelDiscardsPendingChallenge() throws Exception {
        data.activeUser("ana@empresa.test");
        MockHttpSession session = data.login(mockMvc, greenMail, "ana@empresa.test");
        mockMvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection());

        MvcResult step1 = mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD)).andReturn();
        MockHttpSession pendingSession = (MockHttpSession) step1.getRequest().getSession(false);
        mockMvc.perform(post("/login/cancel").session(pendingSession).with(csrf())).andExpect(redirectedUrl("/login"));
        assertThat(challengeRepository.findAll()).allMatch(c -> c.getInvalidatedAt() != null || c.getConsumedAt() != null);
    }

    @Test
    void accountLocksAfterRepeatedPasswordFailures() throws Exception {
        data.activeUser("ana@empresa.test");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", "mala-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/login").with(csrf()).param("email", "ana@empresa.test").param("password", TestData.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bloqueada temporalmente")));
    }
}
