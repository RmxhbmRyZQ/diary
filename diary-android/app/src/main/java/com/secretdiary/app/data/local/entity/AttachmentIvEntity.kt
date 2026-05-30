package com.secretdiary.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attachment_iv")
data class AttachmentIvEntity(
    @PrimaryKey val attachmentId: String,
    @ColumnInfo(name = "iv_b64") val ivB64: String  // 12 字节 IV 的 Base64
)
