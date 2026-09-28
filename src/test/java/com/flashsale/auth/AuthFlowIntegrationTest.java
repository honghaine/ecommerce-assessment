package com.flashsale.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import com.flashsale.IntegrationTest;

class AuthFlowIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Test
    void emailUserCanRegisterVerifyLoginRefreshAndLogout() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD, "VN").andExpect(status().isAccepted());
        verify(email, latestOtp(email)).andExpect(status().isOk());

        JsonNode tokens = loginOk(email, PASSWORD);
        String access = tokens.get("accessToken").asString();
        String refresh = tokens.get("refreshToken").asString();
        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("VN"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.containsString("*")));

        // refresh rotates: new pair works, old refresh token is dead
        JsonNode rotated = json(postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh))
                .andExpect(status().isOk()).andReturn());
        String newAccess = rotated.get("accessToken").asString();
        String newRefresh = rotated.get("refreshToken").asString();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // logout blacklists the access token and revokes the refresh token
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(Map.of("refreshToken", newRefresh))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void phoneUserIsNormalizedAndGetsSmsOtp() throws Exception {
        String local = String.valueOf(ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999));
        String pretty = "+84 91" + local.substring(0, 3) + " " + local.substring(3);
        String e164 = "+8491" + local;

        register(pretty, PASSWORD, "vn").andExpect(status().isAccepted());

        String channel = jdbcTemplate.queryForObject(
                "SELECT channel FROM notification_outbox WHERE recipient = ? ORDER BY id DESC LIMIT 1",
                String.class, e164);
        assertThat(channel).isEqualTo("SMS");

        verify(e164, latestOtp(e164)).andExpect(status().isOk());
        loginOk(pretty, PASSWORD);
    }

    @Test
    void newBuyerGetsWalletWithInitialBalance() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "VN").andExpect(status().isAccepted());

        BigDecimal balance = jdbcTemplate.queryForObject("""
                SELECT w.balance FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.email = ?
                """, BigDecimal.class, email);
        assertThat(balance).isEqualByComparingTo("1000000");
    }

    @Test
    void registerDoesNotRevealExistingAccounts() throws Exception {
        String email = activeUser();
        int outboxBefore = outboxCount(email);

        String fresh = register(uniqueEmail(), PASSWORD, "VN").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String existing = register(email, "Other1234", "VN").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(existing).isEqualTo(fresh);
        assertThat(outboxCount(email)).isEqualTo(outboxBefore);
        // the existing password was not overwritten
        loginOk(email, PASSWORD);
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        String email = activeUser();

        String wrongPassword = login(email, "Wrong1234").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login(uniqueEmail(), "Wrong1234").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(code(wrongPassword)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(code(unknownUser)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(detail(wrongPassword)).isEqualTo(detail(unknownUser));
    }

    @Test
    void unverifiedUserCannotLogin() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "VN").andExpect(status().isAccepted());

        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_VERIFIED"));
    }

    @Test
    void otpIsInvalidatedAfterTooManyWrongAttempts() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "VN").andExpect(status().isAccepted());
        String code = latestOtp(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verify(email, wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        }
        verify(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void otpIsSingleUse() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "VN").andExpect(status().isAccepted());
        String code = latestOtp(email);

        verify(email, code).andExpect(status().isOk());
        verify(email, code).andExpect(status().isBadRequest());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesAllSessions() throws Exception {
        String email = activeUser();
        String first = loginOk(email, PASSWORD).get("refreshToken").asString();
        String otherDevice = loginOk(email, PASSWORD).get("refreshToken").asString();

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first)).andExpect(status().isOk());
        // stolen copy of the already-rotated token is replayed
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first)).andExpect(status().isUnauthorized());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", otherDevice))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRegistrationsCreateExactlyOneUser() throws Exception {
        String email = uniqueEmail();
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return register(email, PASSWORD, "VN").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(202);
            }
        }

        Integer users = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
        Integer wallets = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wallets w JOIN users u ON u.id = w.user_id WHERE u.email = ?",
                Integer.class, email);
        assertThat(users).isEqualTo(1);
        assertThat(wallets).isEqualTo(1);
    }

    @Test
    void invalidInputIsRejectedWithoutEchoingValues() throws Exception {
        register("not-an-identifier", PASSWORD, "VN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTIFIER"));
        register(uniqueEmail(), PASSWORD, "XX").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REGION"));

        String weak = register(uniqueEmail(), "short", "VN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.invalidFields[0]").value("password"))
                .andReturn().getResponse().getContentAsString();
        assertThat(weak).doesNotContain("short");
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private String activeUser() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD, "VN").andExpect(status().isAccepted());
        verify(email, latestOtp(email)).andExpect(status().isOk());
        return email;
    }

    private ResultActions register(String identifier, String password, String region) throws Exception {
        return postJson("/api/v1/auth/register",
                Map.of("identifier", identifier, "password", password, "region", region));
    }

    private ResultActions verify(String identifier, String code) throws Exception {
        return postJson("/api/v1/auth/otp/verify", Map.of("identifier", identifier, "code", code));
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return postJson("/api/v1/auth/login", Map.of("identifier", identifier, "password", password));
    }

    private JsonNode loginOk(String identifier, String password) throws Exception {
        return json(login(identifier, password).andExpect(status().isOk()).andReturn());
    }

    private ResultActions postJson(String path, Map<String, ?> body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private String latestOtp(String recipient) {
        return jdbcTemplate.queryForObject("""
                SELECT payload ->> 'code' FROM notification_outbox
                WHERE recipient = ? AND template = 'OTP_REGISTER'
                ORDER BY id DESC LIMIT 1
                """, String.class, recipient);
    }

    private int outboxCount(String recipient) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox WHERE recipient = ?",
                Integer.class, recipient);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private String code(String body) {
        return jsonMapper.readTree(body).get("code").asString();
    }

    private String detail(String body) {
        return jsonMapper.readTree(body).get("detail").asString();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
