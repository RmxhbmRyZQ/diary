package com.diary.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", columnDefinition = "CHAR(36)", length = 36, nullable = false)
    private String userId;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    @Column(name = "mood", length = 20)
    private String mood;

    @Column(name = "weather", length = 20)
    private String weather;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "encrypted_payload", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String encryptedPayload;

    @Column(name = "iv", length = 32, nullable = false)
    private String iv;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Entry() {}

    public Entry(String id, String userId, LocalDate diaryDate, String mood, String weather,
                 boolean favorite, String encryptedPayload, String iv) {
        this.id = id;
        this.userId = userId;
        this.diaryDate = diaryDate;
        this.mood = mood;
        this.weather = weather;
        this.favorite = favorite;
        this.encryptedPayload = encryptedPayload;
        this.iv = iv;
        this.version = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getDiaryDate() { return diaryDate; }
    public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public String getEncryptedPayload() { return encryptedPayload; }
    public void setEncryptedPayload(String encryptedPayload) { this.encryptedPayload = encryptedPayload; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
