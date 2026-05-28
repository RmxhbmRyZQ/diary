package com.diary.model.dto;

import java.time.LocalDate;

public class UpdateEntryMetaRequest {

    private String mood;
    private String weather;
    private boolean favorite;
    private LocalDate diaryDate;
    private int version;

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public LocalDate getDiaryDate() { return diaryDate; }
    public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
