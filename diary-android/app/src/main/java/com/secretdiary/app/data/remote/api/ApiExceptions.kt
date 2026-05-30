package com.secretdiary.app.data.remote.api

/**
 * 自定义 API 异常。
 */
class UnauthorizedException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message) {
    var serverVersion: Int? = null
    var serverUpdatedAt: String? = null
}
class RateLimitException(val retryAfterSeconds: Int) : Exception("Rate limited")
class TimeSkewException(message: String) : Exception(message)
