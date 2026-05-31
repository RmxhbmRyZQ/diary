package com.diary.service;

import com.diary.config.AppConfig;
import com.diary.exception.BusinessException;
import com.diary.model.dto.*;
import com.diary.model.entity.Session;
import com.diary.model.entity.User;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.SessionRepository;
import com.diary.repository.UserRepository;
import com.diary.security.RateLimiterService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiter;
    private final AttachmentRepository attachmentRepository;
    private final EntryRepository entryRepository;

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository,
                       AppConfig appConfig, ObjectMapper objectMapper,
                       RateLimiterService rateLimiter,
                       AttachmentRepository attachmentRepository, EntryRepository entryRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(appConfig.getSecurity().getBcryptCost());
        this.appConfig = appConfig;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.attachmentRepository = attachmentRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public User register(RegisterRequest req, String clientIp) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException(409, "用户名已存在");
        }

        Map<String, Object> kdfParams = req.getKdfParams();
        Object iterationsObj = kdfParams.get("iterations");
        int iterations = iterationsObj instanceof Integer ? (Integer) iterationsObj : 0;
        if (iterations < appConfig.getKdf().getMinIterations()) {
            throw new BusinessException(400, "KDF 迭代次数低于服务端最小要求");
        }

        if (!rateLimiter.tryRegister(clientIp)) {
            throw new BusinessException(429, "注册请求过于频繁，请稍后再试");
        }

        String kdfParamsJson;
        try {
            kdfParamsJson = objectMapper.writeValueAsString(kdfParams);
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "KDF 参数序列化失败");
        }

        User user = new User(
                UUID.randomUUID().toString(),
                req.getUsername(),
                passwordEncoder.encode(req.getAuthKey()),
                req.getSaltAuth(),
                req.getEncryptedDek(),
                req.getSaltEnc(),
                req.getKdfVersion(),
                kdfParamsJson
        );
        user.setCreatedAt(Instant.now());

        user = userRepository.save(user);
        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Transactional
    public LoginResponse login(LoginRequest req, jakarta.servlet.http.HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByUsername(req.getUsername());
        if (userOpt.isEmpty()) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(req.getAuthKey(), user.getAuthKeyHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        sessionRepository.deleteAllByUserId(user.getId());

        Session session = new Session(
                UUID.randomUUID().toString(),
                user.getId(),
                Instant.now(),
                Instant.now().plusSeconds(appConfig.getSecurity().getSessionMaxAge())
        );
        sessionRepository.save(session);

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("session_id", session.getId());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) appConfig.getSecurity().getSessionMaxAge());
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);

        Map<String, Object> kdfParams;
        try {
            kdfParams = objectMapper.readValue(user.getKdfParams(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            kdfParams = Map.of();
        }

        log.info("User logged in: userId={}", user.getId());
        return new LoginResponse(
                user.getId(),
                user.getEncryptedDek(),
                user.getSaltEnc(),
                user.getKdfVersion(),
                kdfParams,
                user.getRecoveryData() != null
        );
    }

    @Transactional
    public void logout(String sessionId, jakarta.servlet.http.HttpServletResponse response) {
        sessionRepository.deleteById(sessionId);

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("session_id", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        log.info("User logged out: sessionId hash omitted");
    }

    @Transactional
    public void changePassword(String userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (!passwordEncoder.matches(req.getOldAuthKey(), user.getAuthKeyHash())) {
            throw new BusinessException(401, "旧密码验证失败");
        }

        String kdfParamsJson;
        try {
            kdfParamsJson = objectMapper.writeValueAsString(req.getNewKdfParams());
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "KDF 参数序列化失败");
        }

        user.setAuthKeyHash(passwordEncoder.encode(req.getNewAuthKeyHash()));
        user.setEncryptedDek(req.getNewEncryptedDek());
        user.setSaltEnc(req.getNewSaltEnc());
        user.setKdfParams(kdfParamsJson);
        userRepository.save(user);

        sessionRepository.deleteAllByUserId(userId);

        log.info("Password changed for userId={}, all sessions destroyed", userId);
    }

    @Transactional
    public void deleteAccount(String userId, String authKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (!passwordEncoder.matches(authKey, user.getAuthKeyHash())) {
            throw new BusinessException(401, "密码验证失败");
        }

        var attachments = attachmentRepository.findByUserId(userId);

        // 先删除文件，再删除数据库记录，防止中途崩溃导致孤
        for (var att : attachments) {
            try {
                Files.deleteIfExists(Paths.get(att.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete attachment file: {}", att.getFilePath());
            }
        }

        try {
            Path userDir = Paths.get(appConfig.getUpload().getBasePath(), userId);
            Files.deleteIfExists(userDir);
        } catch (IOException e) {
            log.warn("Failed to delete user directory: userId={}", userId);
        }

        entryRepository.deleteAllByUserId(userId);
        attachmentRepository.deleteAllByUserId(userId);
        sessionRepository.deleteAllByUserId(userId);
        userRepository.delete(user);

        log.info("Account deleted: userId={}", userId);
    }

    @Transactional
    public void setRecovery(String userId, SetRecoveryRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        user.setRecoveryData(req.getRecoveryData());
        user.setRecoverySalt(req.getRecoverySalt());
        user.setRecoveryChallenge(req.getChallenge());
        user.setRecoveryChallengeEncrypted(req.getEncryptedChallenge());
        userRepository.save(user);

        log.info("Recovery data set for userId={}", userId);
    }

    @Transactional
    public void deleteRecovery(String userId, String authKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (!passwordEncoder.matches(authKey, user.getAuthKeyHash())) {
            throw new BusinessException(401, "密码验证失败");
        }

        user.setRecoveryData(null);
        user.setRecoverySalt(null);
        user.setRecoveryChallenge(null);
        user.setRecoveryChallengeEncrypted(null);
        userRepository.save(user);

        log.info("Recovery data deleted for userId={}", userId);
    }

    @Transactional
    public void recoveryReset(RecoveryResetRequest req) {
        // 防止用户名枚举：无论用户是否存在或是否设置恢复，统一返回相同错误
        User user = userRepository.findByUsername(req.getUsername()).orElse(null);
        if (user == null
                || user.getRecoveryChallengeEncrypted() == null
                || user.getRecoveryChallengeEncrypted().isEmpty()) {
            throw new BusinessException(400, "该用户未设置恢复口令，无法通过此方式重置密码");
        }

        // 质询-应答验证：证明用户持有正确的恢复口令
        if (req.getEncryptedChallenge() == null || req.getEncryptedChallenge().isBlank()) {
            throw new BusinessException(400, "缺少恢复口令验证凭证");
        }
        if (!req.getEncryptedChallenge().equals(user.getRecoveryChallengeEncrypted())) {
            throw new BusinessException(401, "恢复口令验证失败");
        }

        String kdfParamsJson;
        try {
            kdfParamsJson = objectMapper.writeValueAsString(req.getNewKdfParams());
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "KDF 参数序列化失败");
        }

        user.setAuthKeyHash(passwordEncoder.encode(req.getNewAuthKeyHash()));
        user.setEncryptedDek(req.getNewEncryptedDek());
        user.setSaltEnc(req.getNewSaltEnc());
        user.setKdfParams(kdfParamsJson);
        userRepository.save(user);

        sessionRepository.deleteAllByUserId(user.getId());

        log.info("Recovery reset completed for userId={}, all sessions destroyed", user.getId());
    }

    public Object getRecoveryInfo(String username) {
        // 始终返回 200，防止通过 HTTP 状态码枚举用户名
        // 对于不存在的用户，所有字段返回空字符串
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return Map.of(
                    "recovery_data", "", "recovery_salt", "", "salt_enc", "",
                    "challenge", "", "challenge_iv", ""
            );
        }

        User user = userOpt.get();

        String challengeIv = "";
        if (user.getRecoveryChallengeEncrypted() != null) {
            String[] parts = user.getRecoveryChallengeEncrypted().split(":");
            if (parts.length > 1) {
                challengeIv = parts[1];
            }
        }

        return Map.of(
                "recovery_data", user.getRecoveryData() != null ? user.getRecoveryData() : "",
                "recovery_salt", user.getRecoverySalt() != null ? user.getRecoverySalt() : "",
                "salt_enc", user.getSaltEnc(),
                "challenge", user.getRecoveryChallenge() != null ? user.getRecoveryChallenge() : "",
                "challenge_iv", challengeIv
        );
    }

    public KdfInfoResponse getKdfInfo(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        Map<String, Object> currentParams;
        try {
            currentParams = objectMapper.readValue(user.getKdfParams(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            currentParams = Map.of();
        }

        Map<String, Object> recommendedParams = Map.of(
                "algorithm", appConfig.getKdf().getDefaultAlgorithm(),
                "iterations", appConfig.getKdf().getDefaultIterations()
        );

        return new KdfInfoResponse(
                new KdfInfoResponse.KdfInfo(user.getKdfVersion(), currentParams),
                new KdfInfoResponse.KdfInfo(user.getKdfVersion(), recommendedParams)
        );
    }
}
