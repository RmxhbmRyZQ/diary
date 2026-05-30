package com.secretdiary.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AttachmentUploadResponse(
    val id: String,
    @SerializedName("mime_type") val mimeType: String,
    val sha256: String,
    @SerializedName("created_at") val createdAt: String
)
