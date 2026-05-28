package com.diary.service;

import com.diary.exception.BusinessException;
import com.diary.model.dto.*;
import com.diary.model.entity.Session;
import com.diary.model.entity.User;
import com.diary.repository.SessionRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @MockBean
    private RateLimiterService rateLimiterService;

    private static final String RAW_AUTH_KEY = "raw-test-password-123";
    private static final String USERNAME = "testuser";
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private String hashedAuthKey;

    @BeforeEach
    void setUp() {
        hashedAuthKey = passwordEncoder.encode(RAW_AUTH_KEY);
        when(rateLimiterService.tryRegister(anyString())).thenReturn(true);
    }

    private RegisterRequest createRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(USERNAME);
        req.setAuthKey(RAW_AUTH_KEY);
        req.setSaltAuth("salt-auth-base64");
        req.setEncryptedDek("encrypted-dek-base64");
        req.setEncryptedDekRecovery("encrypted-dek-recovery-base64");
        req.setSaltEnc("salt-enc-base64");
        req.setKdfVersion(1);
        req.setKdfParams(Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000));
        return req;
    }

    private String registerUser() {
        User user = authService.register(createRegisterRequest(), "127.0.0.1");
        return user.getId();
    }

    @Test
    void should_register_user_and_persist_to_database() {
        User user = authService.register(createRegisterRequest(), "127.0.0.1");

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getUsername()).isEqualTo(USERNAME);
        assertThat(passwordEncoder.matches(RAW_AUTH_KEY, found.getAuthKeyHash())).isTrue();
        assertThat(found.getSaltAuth()).isEqualTo("salt-auth-base64");
        assertThat(found.getEncryptedDek()).isEqualTo("encrypted-dek-base64");
        assertThat(found.getEncryptedDekRecovery()).isEqualTo("encrypted-dek-recovery-base64");
        assertThat(found.getSaltEnc()).isEqualTo("salt-enc-base64");
        assertThat(found.getKdfVersion()).isEqualTo(1);
        assertThat(found.getKdfParams()).contains("pbkdf2-sha256");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getId()).hasSize(36);
        assertThat(found.getRecoveryData()).isNull();
        assertThat(found.getRecoverySalt()).isNull();
    }

    @Test
    void should_throw_when_registering_duplicate_username() {
        registerUser();

        assertThatThrownBy(() -> authService.register(createRegisterRequest(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409);

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void should_login_and_create_session_in_database() {
        registerUser();

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setAuthKey(RAW_AUTH_KEY);

        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginResponse loginResp = authService.login(loginReq, response);

        assertThat(loginResp.getUserId()).isNotNull();
        assertThat(loginResp.getEncryptedDek()).isEqualTo("encrypted-dek-base64");
        assertThat(loginResp.getSaltEnc()).isEqualTo("salt-enc-base64");
        assertThat(loginResp.isHasRecovery()).isFalse();

        List<Session> sessions = sessionRepository.findByUserId(loginResp.getUserId());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).isExpired()).isFalse();

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader).isNotNull();
        assertThat(cookieHeader).contains("session_id=");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("Secure");
    }

    @Test
    void should_throw_when_login_with_wrong_credentials() {
        registerUser();

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setAuthKey("wrong-password");

        assertThatThrownBy(() -> authService.login(loginReq, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    void should_throw_when_login_with_nonexistent_username() {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("nonexistent");
        loginReq.setAuthKey(RAW_AUTH_KEY);

        assertThatThrownBy(() -> authService.login(loginReq, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    void should_destroy_all_sessions_on_login() {
        String userId = registerUser();

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setAuthKey(RAW_AUTH_KEY);

        authService.login(loginReq, new MockHttpServletResponse());
        authService.login(loginReq, new MockHttpServletResponse());

        List<Session> sessions = sessionRepository.findByUserId(userId);
        assertThat(sessions).hasSize(1);
    }

    @Test
    void should_change_password_and_update_database() {
        String userId = registerUser();

        // Login first to create a session
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(USERNAME);
        loginReq.setAuthKey(RAW_AUTH_KEY);
        authService.login(loginReq, new MockHttpServletResponse());

        ChangePasswordRequest cpReq = new ChangePasswordRequest();
        cpReq.setOldAuthKey(RAW_AUTH_KEY);
        cpReq.setNewAuthKeyHash("new-raw-password");
        cpReq.setNewEncryptedDek("new-encrypted-dek");
        cpReq.setNewEncryptedDekRecovery("new-dek-recovery");
        cpReq.setNewSaltEnc("new-salt-enc");
        cpReq.setNewKdfParams(Map.of("algorithm", "pbkdf2-sha256", "iterations", 700000));

        authService.changePassword(userId, cpReq);

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("new-raw-password", updated.getAuthKeyHash())).isTrue();
        assertThat(updated.getEncryptedDek()).isEqualTo("new-encrypted-dek");
        assertThat(updated.getEncryptedDekRecovery()).isEqualTo("new-dek-recovery");
        assertThat(updated.getSaltEnc()).isEqualTo("new-salt-enc");
        assertThat(updated.getKdfParams()).contains("700000");

        List<Session> sessions = sessionRepository.findByUserId(userId);
        assertThat(sessions).isEmpty();
    }

    @Test
    void should_delete_account_with_valid_auth_key() {
        String userId = registerUser();

        authService.deleteAccount(userId, RAW_AUTH_KEY);

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    void should_throw_when_delete_account_with_wrong_auth_key() {
        String userId = registerUser();

        assertThatThrownBy(() -> authService.deleteAccount(userId, "wrong-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 401);

        assertThat(userRepository.findById(userId)).isPresent();
    }

    @Test
    void should_set_recovery_and_persist_to_database() {
        String userId = registerUser();

        SetRecoveryRequest req = new SetRecoveryRequest();
        req.setRecoveryData("recovery-data-encrypted");
        req.setRecoverySalt("recovery-salt");

        authService.setRecovery(userId, req);

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getRecoveryData()).isEqualTo("recovery-data-encrypted");
        assertThat(updated.getRecoverySalt()).isEqualTo("recovery-salt");
    }

    @Test
    void should_get_recovery_info_by_username() {
        registerUser();
        String userId = userRepository.findByUsername(USERNAME).orElseThrow().getId();

        SetRecoveryRequest req = new SetRecoveryRequest();
        req.setRecoveryData("recovery-data-encrypted");
        req.setRecoverySalt("recovery-salt");
        authService.setRecovery(userId, req);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) authService.getRecoveryInfo(USERNAME);
        assertThat(info.get("recovery_data")).isEqualTo("recovery-data-encrypted");
        assertThat(info.get("recovery_salt")).isEqualTo("recovery-salt");
        assertThat(info.get("salt_enc")).isEqualTo("salt-enc-base64");
        assertThat(info.get("encrypted_dek_recovery")).isEqualTo("encrypted-dek-recovery-base64");
    }

    @Test
    void should_delete_recovery_and_clear_fields() {
        String userId = registerUser();

        SetRecoveryRequest req = new SetRecoveryRequest();
        req.setRecoveryData("recovery-data-encrypted");
        req.setRecoverySalt("recovery-salt");
        authService.setRecovery(userId, req);

        authService.deleteRecovery(userId, RAW_AUTH_KEY);

        User updated = userRepository.findById(userId).orElseThrow();
        assertThat(updated.getRecoveryData()).isNull();
        assertThat(updated.getRecoverySalt()).isNull();
    }

    @Test
    void should_get_kdf_info_for_user() {
        String userId = registerUser();

        KdfInfoResponse info = authService.getKdfInfo(userId);

        assertThat(info.getCurrent().getKdfVersion()).isEqualTo(1);
        assertThat(info.getCurrent().getKdfParams()).containsEntry("algorithm", "pbkdf2-sha256");
        assertThat(info.getRecommended().getKdfParams()).containsEntry("algorithm", "pbkdf2-sha256");
        assertThat(info.getRecommended().getKdfParams()).containsEntry("iterations", 600000);
    }

    @Test
    void should_throw_not_found_when_kdf_info_for_nonexistent_user() {
        assertThatThrownBy(() -> authService.getKdfInfo("nonexistent-uuid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    void should_throw_not_found_when_change_password_for_nonexistent_user() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldAuthKey("some-key");
        req.setNewAuthKeyHash("some-key");
        req.setNewEncryptedDek("dek");
        req.setNewEncryptedDekRecovery("dekr");
        req.setNewSaltEnc("salt");
        req.setNewKdfParams(Map.of());

        assertThatThrownBy(() -> authService.changePassword("nonexistent", req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }
}
