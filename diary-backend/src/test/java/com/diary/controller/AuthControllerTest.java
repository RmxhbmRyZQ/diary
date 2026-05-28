package com.diary.controller;

import com.diary.model.dto.*;
import com.diary.security.RateLimitFilter;
import com.diary.security.RateLimiterService;
import com.diary.security.SessionFilter;
import com.diary.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private SessionFilter sessionFilter;

    @MockBean
    private RateLimitFilter rateLimitFilter;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_return_400_when_register_with_short_username() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "ab",
                "authKey", "raw-auth-key",
                "saltAuth", "salt",
                "encryptedDek", "dek",
                "encryptedDekRecovery", "dekr",
                "saltEnc", "saltEnc",
                "kdfVersion", 1,
                "kdfParams", Map.of("iterations", 600000)
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_400_when_login_with_empty_username() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "",
                "authKey", "some-key"
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_success_on_valid_register() throws Exception {
        com.diary.model.entity.User mockUser = new com.diary.model.entity.User(
                "test-uuid", "testuser", "hash", "salt", "dek", "dekr", "saltEnc", 1, "{}"
        );
        mockUser.setId("test-uuid");
        mockUser.setCreatedAt(java.time.Instant.now());

        when(authService.register(any(RegisterRequest.class), anyString())).thenReturn(mockUser);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "testuser",
                "authKey", "raw-auth-key",
                "saltAuth", "salt-auth-base64",
                "encryptedDek", "encrypted-dek",
                "encryptedDekRecovery", "encrypted-dek-rec",
                "saltEnc", "salt-enc",
                "kdfVersion", 1,
                "kdfParams", Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000)
        ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user_id").value("test-uuid"));
    }
}
