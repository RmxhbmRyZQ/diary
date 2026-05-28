package com.diary.service;

import com.diary.config.AppConfig;
import com.diary.exception.BusinessException;
import com.diary.model.dto.*;
import com.diary.model.entity.User;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.SessionRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimiterService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private EntryRepository entryRepository;

    private AppConfig appConfig;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        appConfig = new AppConfig();
        appConfig.getSecurity().setBcryptCost(4);
        appConfig.getSecurity().setSessionMaxAge(604800);
        appConfig.getKdf().setDefaultAlgorithm("pbkdf2-sha256");
        appConfig.getKdf().setDefaultIterations(600000);
        appConfig.getKdf().setMinIterations(100000);

        lenient().when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"algorithm\":\"pbkdf2-sha256\",\"iterations\":600000}");
        lenient().when(objectMapper.readValue(any(String.class), any(TypeReference.class)))
                .thenReturn(Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000));
        lenient().when(rateLimiterService.tryRegister(anyString())).thenReturn(true);

        authService = new AuthService(userRepository, sessionRepository, appConfig, objectMapper,
                rateLimiterService, attachmentRepository, entryRepository);
    }

    @Test
    void should_register_user_when_valid_request() {
        RegisterRequest req = createRegisterRequest();
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = authService.register(req, "127.0.0.1");

        assertThat(user.getUsername()).isEqualTo("testuser");
        assertThat(user.getId()).isNotBlank();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void should_throw_when_username_exists() {
        RegisterRequest req = createRegisterRequest();
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 409);
    }

    @Test
    void should_login_successfully_with_valid_credentials() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setAuthKey("test-password-123456");

        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("test-password-123456");
        User user = createUser(hash);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse resp = authService.login(req, response);

        assertThat(resp.getUserId()).isEqualTo(user.getId());
        verify(sessionRepository).deleteAllByUserId(user.getId());
        verify(sessionRepository).save(any());
    }

    @Test
    void should_throw_when_invalid_credentials() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testuser");
        req.setAuthKey("wrong-key");

        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("correct-key");
        User user = createUser(hash);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req, response))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    void should_change_password_and_destroy_sessions() {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldAuthKey("correct-key");
        req.setNewAuthKeyHash("new-raw-key");
        req.setNewEncryptedDek("new-dek");
        req.setNewEncryptedDekRecovery("new-dek-rec");
        req.setNewSaltEnc("new-salt");
        req.setNewKdfParams(Map.of("algorithm", "pbkdf2-sha256", "iterations", 800000));

        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("correct-key");
        User user = createUser(hash);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        authService.changePassword(user.getId(), req);

        var testEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        assertThat(testEncoder.matches("new-raw-key", user.getAuthKeyHash())).isTrue();
        verify(sessionRepository).deleteAllByUserId(user.getId());
    }

    @Test
    void should_delete_account_with_valid_auth_key() {
        String authKey = "test-password-123456";
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode(authKey);
        User user = createUser(hash);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(attachmentRepository.findByUserId(user.getId())).thenReturn(List.of());

        authService.deleteAccount(user.getId(), authKey);

        verify(entryRepository).deleteAllByUserId(user.getId());
        verify(attachmentRepository).findByUserId(user.getId());
        verify(attachmentRepository).deleteAllByUserId(user.getId());
        verify(userRepository).delete(user);
        verify(sessionRepository).deleteAllByUserId(user.getId());
    }

    @Test
    void should_throw_when_delete_account_with_wrong_auth_key() {
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("correct-key");
        User user = createUser(hash);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.deleteAccount(user.getId(), "wrong-key"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 401);
    }

    @Test
    void should_set_and_delete_recovery() {
        SetRecoveryRequest req = new SetRecoveryRequest();
        req.setRecoveryData("recovery-data-encrypted");
        req.setRecoverySalt("recovery-salt");

        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("test-password");
        User user = createUser(hash);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        authService.setRecovery(user.getId(), req);

        assertThat(user.getRecoveryData()).isEqualTo("recovery-data-encrypted");

        authService.deleteRecovery(user.getId(), "test-password");
        assertThat(user.getRecoveryData()).isNull();
    }

    private RegisterRequest createRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("testuser");
        req.setAuthKey("raw-auth-key");
        req.setSaltAuth("salt-auth-base64");
        req.setEncryptedDek("encrypted-dek-base64");
        req.setEncryptedDekRecovery("encrypted-dek-rec-base64");
        req.setSaltEnc("salt-enc-base64");
        req.setKdfVersion(1);
        req.setKdfParams(Map.of("algorithm", "pbkdf2-sha256", "iterations", 600000));
        return req;
    }

    private User createUser(String authKeyHash) {
        String userId = UUID.randomUUID().toString();
        User user = new User(
                userId, "testuser", authKeyHash,
                "salt-auth", "enc-dek", "enc-dek-rec", "salt-enc",
                1, "{\"algorithm\":\"pbkdf2-sha256\",\"iterations\":600000}"
        );
        user.setId(userId);
        return user;
    }
}
