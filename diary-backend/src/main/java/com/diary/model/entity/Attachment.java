package com.diary.model.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String id;

    @Column(name = "diary_id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String diaryId;

    @Column(name = "user_id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String userId;

    @Column(name = "file_path", length = 512, nullable = false)
    private String filePath;

    @Column(name = "iv", length = 32, nullable = false)
    private String iv;

    @Column(name = "mime_type", length = 50, nullable = false)
    private String mimeType;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Attachment() {}

    public Attachment(String id, String diaryId, String userId, String filePath,
                      String iv, String mimeType, String sha256) {
        this.id = id;
        this.diaryId = diaryId;
        this.userId = userId;
        this.filePath = filePath;
        this.iv = iv;
        this.mimeType = mimeType;
        this.sha256 = sha256;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDiaryId() { return diaryId; }
    public void setDiaryId(String diaryId) { this.diaryId = diaryId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
