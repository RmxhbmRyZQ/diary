package com.diary.controller;

import com.diary.model.entity.AdminUser;
import com.diary.model.entity.User;
import com.diary.repository.AdminUserRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimitFilter;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntryRepository entryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionFilter sessionFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    private static final String ADMIN_USERNAME = "admin";
    private static final String RAW_PASSWORD = "admin-pass-123";
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() throws Exception {
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
    }

    private void seedAdmin() {
        String hashedPassword = passwordEncoder.encode(RAW_PASSWORD);
        AdminUser admin = new AdminUser(UUID.randomUUID().toString(), ADMIN_USERNAME, hashedPassword);
        adminUserRepository.save(admin);
    }

    private String loginAndGetToken() throws Exception {
        seedAdmin();

        Map<String, String> body = Map.of("username", ADMIN_USERNAME, "password", RAW_PASSWORD);
        MvcResult result = mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> respMap = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) ((Map<String, Object>) respMap.get("data")).get("token");
    }

    @Test
    void should_login_successfully_and_return_token() throws Exception {
        seedAdmin();

        Map<String, String> body = Map.of("username", ADMIN_USERNAME, "password", RAW_PASSWORD);

        mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void should_return_400_when_username_empty() throws Exception {
        Map<String, String> body = Map.of("username", "", "password", RAW_PASSWORD);

        mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_return_400_when_password_empty() throws Exception {
        Map<String, String> body = Map.of("username", ADMIN_USERNAME, "password", "");

        mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_return_401_when_wrong_password() throws Exception {
        seedAdmin();

        Map<String, String> body = Map.of("username", ADMIN_USERNAME, "password", "wrong-password");

        mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_return_401_when_nonexistent_admin() throws Exception {
        Map<String, String> body = Map.of("username", "nonexistent", "password", RAW_PASSWORD);

        mockMvc.perform(post("/admin/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_return_401_when_access_without_bearer_token() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_return_401_when_access_with_invalid_token() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void should_list_users_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        User user = new User(UUID.randomUUID().toString(), "testuser", "hash",
                "salt", "dek", "saltEnc", 1, "{}");
        userRepository.save(user);

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].username").value("testuser"))
                .andExpect(jsonPath("$.data[0].entry_count").value(0));
    }

    @Test
    void should_delete_user_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        User user = new User(UUID.randomUUID().toString(), "testuser", "hash",
                "salt", "dek", "saltEnc", 1, "{}");
        userRepository.save(user);

        mockMvc.perform(delete("/admin/users/" + user.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void should_return_404_when_deleting_nonexistent_user() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(delete("/admin/users/nonexistent-id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void should_get_dashboard_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        User user = new User(UUID.randomUUID().toString(), "testuser", "hash",
                "salt", "dek", "saltEnc", 1, "{}");
        userRepository.save(user);

        mockMvc.perform(get("/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total_users").value(1))
                .andExpect(jsonPath("$.data.total_entries").value(0));
    }

    @Test
    void should_logout_successfully() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(post("/admin/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void should_update_kdf_config_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("algorithm", "pbkdf2-sha256", "iterations", 500000);

        mockMvc.perform(put("/admin/config/kdf")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.algorithm").value("pbkdf2-sha256"))
                .andExpect(jsonPath("$.data.iterations").value(500000));
    }

    @Test
    void should_return_400_when_kdf_algorithm_empty() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("algorithm", "", "iterations", 500000);

        mockMvc.perform(put("/admin/config/kdf")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_return_400_when_rate_limit_invalid() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("endpoint", "api", "limit", 0);

        mockMvc.perform(put("/admin/config/rate-limit")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void should_update_rate_limit_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("endpoint", "login", "limit", 10);

        mockMvc.perform(put("/admin/config/rate-limit")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.endpoint").value("login"))
                .andExpect(jsonPath("$.data.new_limit").value(10));
    }

    @Test
    void should_update_attachment_limits_with_valid_token() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("maxFileSizeMb", 20, "maxPerEntry", 10);

        mockMvc.perform(put("/admin/config/attachments")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.max_file_size_mb").value(20))
                .andExpect(jsonPath("$.data.max_per_entry").value(10));
    }

    @Test
    void should_return_400_when_attachment_limits_invalid() throws Exception {
        String token = loginAndGetToken();

        Map<String, Object> body = Map.of("maxFileSizeMb", 200, "maxPerEntry", 0);

        mockMvc.perform(put("/admin/config/attachments")
                        .contentType("application/json")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
