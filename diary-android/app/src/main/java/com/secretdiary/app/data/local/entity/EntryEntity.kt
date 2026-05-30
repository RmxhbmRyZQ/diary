package com.secretdiary.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entries",
    indices = [Index("diary_date", unique = true)]
)
data class EntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "diary_date") val diaryDate: String,      // yyyy-MM-dd
    val title: String,
    val content: String,                                          // Markdown 原文
    val summary: String,                                          // 前50字符纯文本
    val tags: String,                                             // JSON 数组字符串
    val mood: String?,
    val weather: String?,
    val favorite: Boolean,
    @ColumnInfo(name = "attachment_ids") val attachmentIds: String, // JSON 数组字符串
    @ColumnInfo(name = "server_version") val serverVersion: Int?,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: String?, // ISO 8601 北京时间
    @ColumnInfo(name = "local_updated_at") val localUpdatedAt: String?,   // ISO 8601 北京时间
    @ColumnInfo(name = "is_dirty") val isDirty: Boolean
)
