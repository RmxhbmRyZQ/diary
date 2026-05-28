package com.diary.model.dto;

import com.diary.model.entity.Entry;
import java.time.Instant;
import java.time.LocalDate;

public class EntryResponse {

    private String id;
    private LocalDate diaryDate;
    private String mood;
    private String weather;
    private boolean favorite;
    private String encryptedPayload;
    private String iv;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public static EntryResponse from(Entry entry) {
        EntryResponse r = new EntryResponse();
        r.id = entry.getId();
        r.diaryDate = entry.getDiaryDate();
        r.mood = entry.getMood();
        r.weather = entry.getWeather();
        r.favorite = entry.isFavorite();
        r.encryptedPayload = entry.getEncryptedPayload();
        r.iv = entry.getIv();
        r.version = entry.getVersion();
        r.createdAt = entry.getCreatedAt();
        r.updatedAt = entry.getUpdatedAt();
        return r;
    }

    public String getId() { return id; }
    public LocalDate getDiaryDate() { return diaryDate; }
    public String getMood() { return mood; }
    public String getWeather() { return weather; }
    public boolean isFavorite() { return favorite; }
    public String getEncryptedPayload() { return encryptedPayload; }
    public String getIv() { return iv; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
