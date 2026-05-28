package com.diary.service;

import com.diary.config.AppConfig;
import com.diary.exception.BusinessException;
import com.diary.model.entity.AdminUser;
import com.diary.model.entity.Attachment;
import com.diary.model.entity.User;
import com.diary.repository.AdminUserRepository;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import com.diary.repository.SessionRepository;
import com.diary.repository.UserRepository;
import com.diary.security.AdminFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final EntryRepository entryRepository;
    private final AttachmentRepository attachmentRepository;
    private final SessionRepository sessionRepository;
    private final AttachmentService attachmentService;
    private final AppConfig appConfig;
    private final AdminFilter adminFilter;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminService(AdminUserRepository adminUserRepository, UserRepository userRepository,
                        EntryRepository entryRepository, AttachmentRepository attachmentRepository,
                        SessionRepository sessionRepository,
                        AttachmentService attachmentService,
                        AppConfig appConfig, AdminFilter adminFilter) {
        this.adminUserRepository = adminUserRepository;
        this.userRepository = userRepository;
        this.entryRepository = entryRepository;
        this.attachmentRepository = attachmentRepository;
        this.sessionRepository = sessionRepository;
        this.attachmentService = attachmentService;
        this.appConfig = appConfig;
        this.adminFilter = adminFilter;
        this.passwordEncoder = new BCryptPasswordEncoder(appConfig.getSecurity().getBcryptCost());
    }

    public Map<String, Object> login(String username, String password) {
        Optional<AdminUser> adminOpt = adminUserRepository.findByUsername(username);
        if (adminOpt.isEmpty()) {
            throw new BusinessException(401, "管理员用户名或密码错误");
        }

        AdminUser admin = adminOpt.get();
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            throw new BusinessException(401, "管理员用户名或密码错误");
        }

        String token = UUID.randomUUID().toString();
        adminFilter.registerSession(token, admin.getId());

        log.info("Admin logged in: adminId={}", admin.getId());
        return Map.of("token", token);
    }

    public void logout(String token) {
        adminFilter.removeSession(token);
    }

    public List<Map<String, Object>> listUsers() {
        List<User> users = userRepository.findAll();
        Map<String, Long> countByUserId = new HashMap<>();
        for (Object[] row : entryRepository.countByUserIds()) {
            countByUserId.put((String) row[0], (Long) row[1]);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", user.getId());
            info.put("username", user.getUsername());
            info.put("created_at", user.getCreatedAt());
            info.put("entry_count", countByUserId.getOrDefault(user.getId(), 0L));
            result.add(info);
        }
        return result;
    }

    @Transactional
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        List<Attachment> attachments = attachmentRepository.findByUserId(userId);

        entryRepository.deleteAllByUserId(userId);
        attachmentRepository.deleteAllByUserId(userId);
        sessionRepository.deleteAllByUserId(userId);
        userRepository.delete(user);

        for (Attachment att : attachments) {
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

        log.info("Admin deleted user: userId={}", userId);
    }

    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("total_users", userRepository.count());
        dashboard.put("total_entries", entryRepository.count());
        dashboard.put("storage_bytes", attachmentService.totalStorageBytes());
        return dashboard;
    }

    public Map<String, Object> updateKdfConfig(String algorithm, int iterations) {
        appConfig.getKdf().setDefaultAlgorithm(algorithm);
        appConfig.getKdf().setDefaultIterations(iterations);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", algorithm);
        result.put("iterations", iterations);
        log.info("Admin updated KDF config: algorithm={}, iterations={}", algorithm, iterations);
        return result;
    }

    public Map<String, Object> updateRateLimit(String endpoint, int limit) {
        AppConfig.RateLimit rl = appConfig.getRateLimit();
        switch (endpoint) {
            case "login" -> rl.setLoginPerMinute(limit);
            case "register" -> rl.setRegisterPerHour(limit);
            case "recovery" -> rl.setRecoveryPerMinute(limit);
            case "api" -> rl.setApiPerMinute(limit);
            case "attachment" -> rl.setAttachmentPerMinute(limit);
            default -> throw new BusinessException(400, "未知的限流端点: " + endpoint);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", endpoint);
        result.put("new_limit", limit);
        log.info("Admin updated rate limit: endpoint={}, limit={}", endpoint, limit);
        return result;
    }

    public Map<String, Object> updateAttachmentLimits(int maxSizeMb, int maxPerEntry) {
        appConfig.getUpload().setMaxFileSizeMb(maxSizeMb);
        appConfig.getUpload().setMaxPerEntry(maxPerEntry);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("max_file_size_mb", maxSizeMb);
        result.put("max_per_entry", maxPerEntry);
        log.info("Admin updated attachment limits: maxSize={}, maxPerEntry={}", maxSizeMb, maxPerEntry);
        return result;
    }
}
