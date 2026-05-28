package com.diary.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public class CreateEntryRequest {

    @NotNull
    private LocalDate diaryDate;

    private String mood;
    private String weather;
    private boolean favorite;

    @NotBlank
    private String encryptedPayload;

    @NotBlank
    private String iv;

    private List<String> attachmentIds;

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

    public List<String> getAttachmentIds() { return attachmentIds; }
    public void setAttachmentIds(List<String> attachmentIds) { this.attachmentIds = attachmentIds; }
}
