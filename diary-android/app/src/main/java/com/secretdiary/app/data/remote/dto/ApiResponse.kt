package com.secretdiary.app.data.remote.dto

/**
 * 统一后端响应包装。
 */
data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

/**
 * 冲突错误 (409) 响应体。
 */
data class ConflictData(
    val version: Int?,
    @com.google.gson.annotations.SerializedName("updated_at") val updatedAt: String?
)
