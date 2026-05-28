package com.diary.controller;

import com.diary.model.entity.Session;
import com.diary.model.entity.User;
import com.diary.repository.SessionRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimitFilter;
import com.diary.security.RateLimiterService;
import com.diary.security.SessionFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionFilter sessionFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private RateLimiterService rateLimiterService;

    private static final String RAW_AUTH_KEY = "raw-test-password-123";
    private static final String USERNAME = "testuser";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private String hashedAuthKey;

    @BeforeEach
    void setUp() throws Exception {
        hashedAuthKey = passwordEncoder.encode(RAW_AUTH_KEY);

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(sessionFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(rateLimitFilter).doFilter(any(), any(), any());

        when(rateLimiterService.tryRegister(anyString())).thenReturn(true);
        when(rateLimiterService.tryLogin(anyString())).thenReturn(true);
        when(rateLimiterService.tryRecovery(anyString())).thenReturn(true);
    }

    private String registerUser() throws Exception {
        Map<String, Object> body = Map.of(
                "username", USERNAME,
                "authKey", RAW_AUTH_KEY,
                "saltAuth", "salt-auth-base64",
                "encryptedDek", "encrypted-dek-base64",
                "encryptedDekRecovery", "encrypted-dek-recovery-base64",
                "saltEnc", "salt-enc-base64",
                "kdfVersion", 1,
                "kdfParams", Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000)
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user_id").isNotEmpty())
                .andReturn();

        Map<String, Object> respMap = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) ((Map<String, Object>) respMap.get("data")).get("user_id");
    }

    private String loginAndGetSessionId() throws Exception {
        Map<String, String> body = Map.of("username", USERNAME, "authKey", RAW_AUTH_KEY);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        return extractSessionIdFromCookie(result);
    }

    private String extractSessionIdFromCookie(MvcResult result) {
        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith("session_id=")) {
                    return cookie.substring("session_id=".length(), cookie.indexOf(";"));
                }
            }
        }
        return null;
    }

    @Test
    void should_register_user_and_persist_to_database() throws Exception {
        String userId = registerUser();

        User found = userRepository.findById(userId).orElseThrow();
        assertThat(found.getUsername()).isEqualTo(USERNAME);
        assertThat(passwordEncoder.matches(RAW_AUTH_KEY, found.getAuthKeyHash())).isTrue();
        assertThat(found.getSaltAuth()).isEqualTo("salt-auth-base64");
        assertThat(found.getEncryptedDek()).isEqualTo("encrypted-dek-base64");
        assertThat(found.getSaltEnc()).isEqualTo("salt-enc-base64");
        assertThat(found.getKdfVersion()).isEqualTo(1);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void should_return_409_on_duplicate_registration() throws Exception {
        registerUser();

        Map<String, Object> body = Map.of(
                "username", USERNAME,
                "authKey", RAW_AUTH_KEY,
                "saltAuth", "salt-auth-base64",
                "encryptedDek", "encrypted-dek-base64",
                "encryptedDekRecovery", "encrypted-dek-recovery-base64",
                "saltEnc", "salt-enc-base64",
                "kdfVersion", 1,
                "kdfParams", Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void should_return_400_when_username_too_short() throws Exception {
        Map<String, Object> body = Map.of(
                "username", "ab",
                "authKey", RAW_AUTH_KEY,
                "saltAuth", "salt",
                "encryptedDek", "dek",
                "encryptedDekRecovery", "dekr",
                "saltEnc", "salt",
                "kdfVersion", 1,
                "kdfParams", Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000)
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_login_and_create_session_with_cookie() throws Exception {
        registerUser();

        Map<String, String> body = Map.of("username", USERNAME, "authKey", RAW_AUTH_KEY);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.encryptedDek").value("encrypted-dek-base64"))
                .andExpect(jsonPath("$.data.saltEnc").value("salt-enc-base64"))
                .andExpect(jsonPath("$.data.hasRecovery").value(false))
                .andReturn();

        String sessionId = extractSessionIdFromCookie(result);
        assertThat(sessionId).isNotNull();
        assertThat(sessionId).isNotEmpty();

        Optional<Session> session = sessionRepository.findById(sessionId);
        assertThat(session).isPresent();
        assertThat(session.get().isExpired()).isFalse();
    }

    @Test
    void should_return_401_on_wrong_credentials() throws Exception {
        registerUser();

        Map<String, String> body = Map.of("username", USERNAME, "authKey", "wrong-password");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_logout_and_delete_session() throws Exception {
        String userId = registerUser();
        String sessionId = loginAndGetSessionId();

        assertThat(sessionRepository.findById(sessionId)).isPresent();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .requestAttr("userId", userId)
                        .requestAttr("sessionId", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(sessionRepository.findById(sessionId)).isEmpty();
    }

    @Test
    void should_change_password_and_verify_database() throws Exception {
        String userId = registerUser();
        loginAndGetSessionId();

        Map<String, Object> body = Map.of(
                "oldAuthKey", RAW_AUTH_KEY,
                "newAuthKeyHash", "new-raw-password",
                "newEncryptedDek", "new-dek",
                "newEncryptedDekRecovery", "new-dekr",
                "newSaltEnc", "new-salt",
                "newKdfParams", Map.of("algorithm", "pbkdf2-sha256", "iterations", 800000)
        );

        mockMvc.perform(put("/api/v1/auth/password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("new-raw-password", updated.getAuthKeyHash())).isTrue();
        assertThat(updated.getEncryptedDek()).isEqualTo("new-dek");
        assertThat(updated.getSaltEnc()).isEqualTo("new-salt");

        List<Session> sessions = sessionRepository.findByUserId(userId);
        assertThat(sessions).isEmpty();
    }

    @Test
    void should_set_recovery_and_verify_database() throws Exception {
        String userId = registerUser();

        Map<String, String> body = Map.of(
                "recoveryData", "recovery-data-encrypted",
                "recoverySalt", "recovery-salt-value"
        );

        mockMvc.perform(put("/api/v1/auth/recovery")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getRecoveryData()).isEqualTo("recovery-data-encrypted");
        assertThat(updated.getRecoverySalt()).isEqualTo("recovery-salt-value");
    }

    @Test
    void should_get_recovery_info_by_username() throws Exception {
        String userId = registerUser();

        Map<String, String> body = Map.of(
                "recoveryData", "recovery-data-encrypted",
                "recoverySalt", "recovery-salt-value"
        );

        mockMvc.perform(put("/api/v1/auth/recovery")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/recovery")
                        .param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recovery_data").value("recovery-data-encrypted"))
                .andExpect(jsonPath("$.data.recovery_salt").value("recovery-salt-value"))
                .andExpect(jsonPath("$.data.salt_enc").value("salt-enc-base64"))
                .andExpect(jsonPath("$.data.encrypted_dek_recovery").value("encrypted-dek-recovery-base64"));
    }

    @Test
    void should_get_recovery_info_for_user_without_recovery() throws Exception {
        registerUser();

        mockMvc.perform(get("/api/v1/auth/recovery")
                        .param("username", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recovery_data").value(""));
    }

    @Test
    void should_delete_recovery_and_clear_database() throws Exception {
        String userId = registerUser();
        Map<String, String> body = Map.of(
                "recoveryData", "recovery-data-encrypted",
                "recoverySalt", "recovery-salt-value"
        );
        mockMvc.perform(put("/api/v1/auth/recovery")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk());

        Map<String, String> deleteBody = Map.of("authKey", RAW_AUTH_KEY);
        mockMvc.perform(delete("/api/v1/auth/recovery")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(deleteBody))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getRecoveryData()).isNull();
        assertThat(updated.getRecoverySalt()).isNull();
    }

    @Test
    void should_delete_account_and_verify_database() throws Exception {
        String userId = registerUser();

        Map<String, String> body = Map.of("authKey", RAW_AUTH_KEY);

        mockMvc.perform(delete("/api/v1/auth/account")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(userId)).isEmpty();
    }

    @Test
    void should_get_kdf_info() throws Exception {
        String userId = registerUser();

        mockMvc.perform(get("/api/v1/auth/kdf-info")
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.current.kdfVersion").value(1))
                .andExpect(jsonPath("$.data.current.kdfParams.algorithm").value("pbkdf2-sha256"))
                .andExpect(jsonPath("$.data.recommended.kdfParams.algorithm").value("pbkdf2-sha256"));
    }
}
