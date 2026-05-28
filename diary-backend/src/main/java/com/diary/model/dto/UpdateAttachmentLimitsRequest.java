package com.diary.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateAttachmentLimitsRequest {

    @Min(value = 1, message = "最大文件大小至少为1MB")
    @Max(value = 100, message = "最大文件大小不能超过100MB")
    private int maxFileSizeMb;

    @Min(value = 1, message = "每篇日记附件数至少为1")
    @Max(value = 50, message = "每篇日记附件数不能超过50")
    private int maxPerEntry;

    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }

    public int getMaxPerEntry() { return maxPerEntry; }
    public void setMaxPerEntry(int maxPerEntry) { this.maxPerEntry = maxPerEntry; }
}
