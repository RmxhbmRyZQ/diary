package com.diary.service;

import com.diary.config.AppConfig;
import com.diary.exception.BusinessException;
import com.diary.model.entity.Attachment;
import com.diary.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AppConfig appConfig;

    @InjectMocks
    private AttachmentService attachmentService;

    private AppConfig.Upload uploadConfig;
    private String userId;

    @BeforeEach
    void setUp() {
        uploadConfig = new AppConfig.Upload();
        uploadConfig.setBasePath("./target/test-uploads");
        uploadConfig.setMaxFileSizeMb(10);
        uploadConfig.setMaxPerEntry(20);
        uploadConfig.setUnattachedTtlHours(24);

        lenient().when(appConfig.getUpload()).thenReturn(uploadConfig);
        userId = UUID.randomUUID().toString();
    }

    @Test
    void should_throw_when_file_too_large() {
        uploadConfig.setMaxFileSizeMb(0); // effectively limit to 0 bytes
        MultipartFile file = new MockMultipartFile("file", "test.png",
                "image/png", new byte[100]);

        assertThatThrownBy(() -> attachmentService.upload(userId, "diary-1", file, "iv123", "sha256"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    void should_throw_when_attachment_limit_exceeded() {
        when(attachmentRepository.countByDiaryId("diary-1")).thenReturn(20L);
        MultipartFile file = new MockMultipartFile("file", "test.png",
                "image/png", new byte[10]);

        assertThatThrownBy(() -> attachmentService.upload(userId, "diary-1", file, "iv123", "sha256"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 400);
    }

    @Test
    void should_throw_when_attachment_not_found() {
        when(attachmentRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.getAttachment(userId, "nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 404);
    }

    @Test
    void should_throw_forbidden_when_wrong_user() {
        Attachment att = new Attachment("att-id", "diary-1", "other-user",
                "/tmp/file", "iv", "image/png", "sha256hash");
        when(attachmentRepository.findById("att-id")).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> attachmentService.getAttachment("my-user", "att-id"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 403);
    }
}
