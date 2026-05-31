package com.diary.service;

import com.diary.config.AppConfig;
import com.diary.exception.BusinessException;
import com.diary.model.entity.Attachment;
import com.diary.repository.AttachmentRepository;
import com.diary.repository.EntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;
    private final AppConfig appConfig;
    private final EntryRepository entryRepository;

    public AttachmentService(AttachmentRepository attachmentRepository, AppConfig appConfig,
                            EntryRepository entryRepository) {
        this.attachmentRepository = attachmentRepository;
        this.appConfig = appConfig;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public Attachment upload(String userId, String diaryId, MultipartFile file, String iv, String sha256) {
        if (file.getSize() > appConfig.getUpload().getMaxFileSizeMb() * 1024L * 1024L) {
            throw new BusinessException(400, "文件大小超过限制");
        }

        // 仅对已存在的日记校验所有权；占位 diary_id 跳过检查
        if (!"00000000-0000-0000-0000-000000000000".equals(diaryId)
                && entryRepository.existsById(diaryId)
                && !entryRepository.existsByIdAndUserId(diaryId, userId)) {
            throw new BusinessException(403, "无权操作该日记");
        }

        // 按用户+日记维度限制附件数，防止跨用户耗尽配额
        long count = attachmentRepository.countByDiaryIdAndUserId(diaryId, userId);
        if (count >= appConfig.getUpload().getMaxPerEntry()) {
            throw new BusinessException(400, "附件数量已达上限");
        }

        String attachmentId = UUID.randomUUID().toString();
        String mimeType = file.getContentType();
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }

        Path baseDir = Paths.get(appConfig.getUpload().getBasePath()).toAbsolutePath().normalize();
        Path userDir = baseDir.resolve(userId);
        try {
            Files.createDirectories(userDir);
        } catch (IOException e) {
            throw new BusinessException(500, "文件存储目录创建失败");
        }

        Path filePath = userDir.resolve(attachmentId);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException(500, "文件写入失败");
        }

        Attachment attachment = new Attachment(
                attachmentId, diaryId, userId, filePath.toString(), iv, mimeType, sha256
        );
        attachment = attachmentRepository.save(attachment);

        log.info("Attachment uploaded: id={}, userId={}, diaryId={}, mimeType={}, sha256={}",
                attachmentId, userId, diaryId, mimeType, sha256);
        return attachment;
    }

    public Attachment getAttachment(String userId, String attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(404, "附件不存在"));

        if (!attachment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该附件");
        }

        return attachment;
    }

    @Transactional
    public void delete(String userId, String attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(404, "附件不存在"));

        if (!attachment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该附件");
        }

        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete attachment file: {}", attachment.getFilePath());
        }

        attachmentRepository.delete(attachment);
        log.info("Attachment deleted: id={}, userId={}", attachmentId, userId);
    }

    @Transactional
    public void deleteByDiaryId(String diaryId) {
        List<Attachment> attachments = attachmentRepository.findByDiaryId(diaryId);
        log.info("Deleting {} attachments for diary: {}", attachments.size(), diaryId);
        for (Attachment att : attachments) {
            try {
                boolean deleted = Files.deleteIfExists(Paths.get(att.getFilePath()));
                log.info("Attachment file {}: path={}, diaryId={}",
                        deleted ? "deleted" : "not found", att.getFilePath(), diaryId);
            } catch (IOException e) {
                log.warn("Failed to delete attachment file: {}", att.getFilePath(), e);
            }
        }
        attachmentRepository.deleteAllByDiaryId(diaryId);
    }

    @Transactional
    public void deleteAllByUserId(String userId) {
        List<Attachment> attachments = attachmentRepository.findByUserId(userId);
        for (Attachment att : attachments) {
            try {
                Files.deleteIfExists(Paths.get(att.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete attachment file: {}", att.getFilePath());
            }
        }
        attachmentRepository.deleteAllByUserId(userId);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupUnattached() {
        Instant threshold = Instant.now().minusSeconds(
                appConfig.getUpload().getUnattachedTtlHours() * 3600L);
        List<Attachment> unattached = attachmentRepository
                .findByDiaryIdAndCreatedAtBefore("00000000-0000-0000-0000-000000000000", threshold);

        for (Attachment att : unattached) {
            try {
                // 先删除 DB 记录，防止事务回滚时文件已删导致数据不一致
                attachmentRepository.delete(att);
                Files.deleteIfExists(Paths.get(att.getFilePath()));
                log.info("Cleaned up unattached attachment: id={}", att.getId());
            } catch (IOException e) {
                log.warn("Failed to clean up attachment file: {}", att.getFilePath());
            }
        }
    }

    public long totalStorageBytes() {
        long total = 0;
        List<Attachment> all = attachmentRepository.findAll();
        for (Attachment att : all) {
            try {
                total += Files.size(Paths.get(att.getFilePath()));
            } catch (IOException e) {
                // file missing, skip
            }
        }
        return total;
    }
}
