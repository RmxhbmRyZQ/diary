package com.secretdiary.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entries",
    indices = [Index(value = ["user_id", "diary_date"], unique = true)]
)
data class EntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,             // 当前登录用户名，用于多账户隔离
    @ColumnInfo(name = "diary_date") val diaryDate: String,      // yyyy-MM-dd
    val title: String,                                            // 明文标题
    @ColumnInfo(name = "encrypted_content") val encryptedContent: String, // AES-256-GCM 加密后的日记正文
    @ColumnInfo(name = "content_iv") val contentIv: String,       // 内容加密 IV（Base64）
    val summary: String,                                          // 前50字符纯文本，用于列表预览
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
