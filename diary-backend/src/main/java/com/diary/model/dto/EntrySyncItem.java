package com.diary.model.dto;

import java.time.Instant;
import java.time.LocalDate;

public class EntrySyncItem {

    private String id;
    private LocalDate diaryDate;
    private Instant updatedAt;

    public EntrySyncItem(String id, LocalDate diaryDate, Instant updatedAt) {
        this.id = id;
        this.diaryDate = diaryDate;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public LocalDate getDiaryDate() { return diaryDate; }
    public Instant getUpdatedAt() { return updatedAt; }
}
