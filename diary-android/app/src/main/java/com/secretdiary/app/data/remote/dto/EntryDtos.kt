package com.secretdiary.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ---------- 同步摘要 ----------

data class SyncResponse(
    val entries: List<SyncEntry>
)

data class SyncEntry(
    val id: String,
    val diaryDate: String,
    val updatedAt: String
)

// ---------- 批量获取详情 ----------

data class BatchEntryResponse(
    val entries: List<EntryResponse>
)

data class EntryResponse(
    val id: String,
    val diaryDate: String,
    val mood: String?,
    val weather: String?,
    val favorite: Boolean,
    val encryptedPayload: String,
    val iv: String,
    val version: Int,
    val createdAt: String,
    val updatedAt: String
)

// ---------- 创建日记 ----------

data class CreateEntryRequest(
    val diaryDate: String,
    val mood: String?,
    val weather: String?,
    val favorite: Boolean = false,
    val encryptedPayload: String,
    val iv: String,
    val attachmentIds: List<String>? = null
)

// ---------- 更新日记 ----------

data class UpdateEntryRequest(
    val diaryDate: String,
    val mood: String?,
    val weather: String?,
    val favorite: Boolean = false,
    val encryptedPayload: String,
    val iv: String,
    val version: Int
)

// ---------- 部分更新元数据 ----------

data class UpdateMetaRequest(
    val mood: String?,
    val weather: String?,
    val favorite: Boolean?,
    val diaryDate: String?,
    val version: Int
)
