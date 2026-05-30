package com.secretdiary.app.domain.model

/**
 * 日记业务模型（解密后的明文数据）。
 */
data class Diary(
    val id: String,
    val diaryDate: String,        // yyyy-MM-dd
    val title: String,
    val content: String,          // Markdown 原文
    val summary: String,
    val tags: List<String>,
    val mood: String?,
    val weather: String?,
    val favorite: Boolean,
    val attachmentIds: List<String>,
    val serverVersion: Int?,
    val serverUpdatedAt: String?, // ISO 8601 北京时间
    val localUpdatedAt: String?,   // ISO 8601 北京时间
    val isDirty: Boolean
)
