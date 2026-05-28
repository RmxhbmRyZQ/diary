package com.diary.controller;

import com.diary.model.dto.ApiResponse;
import com.diary.model.entity.Attachment;
import com.diary.service.AttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> upload(
            @RequestParam("diary_id") String diaryId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("iv") String iv,
            @RequestParam("sha256") String sha256,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        Attachment attachment = attachmentService.upload(userId, diaryId, file, iv, sha256);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("id", attachment.getId(),
                       "mime_type", attachment.getMimeType(),
                       "sha256", attachment.getSha256(),
                       "created_at", attachment.getCreatedAt())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        Attachment attachment = attachmentService.getAttachment(userId, id);

        Path filePath = Path.of(attachment.getFilePath());
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getId() + "\"")
                .header("X-Content-SHA256", attachment.getSha256())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable String id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        attachmentService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("已删除", null));
    }
}
