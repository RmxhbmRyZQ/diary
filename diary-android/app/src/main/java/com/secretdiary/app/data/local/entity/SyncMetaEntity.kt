package com.secretdiary.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String  // 如 last_sync_timestamp (ISO 8601 北京时间)
)
