package com.secretdiary.app.data.remote.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局 API 错误事件总线。拦截器捕获 HTTP 错误码后通过 SharedFlow 发送事件，UI 层观察并展示。
 * 避免在 OkHttp 线程抛异常导致崩溃。
 */
@Singleton
class GlobalErrorHandler @Inject constructor() {
    private val _sessionExpired = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Boolean> = _sessionExpired.asSharedFlow()

    private val _rateLimited = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val rateLimited: SharedFlow<Int> = _rateLimited.asSharedFlow()

    private val _timeSkew = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val timeSkew: SharedFlow<String> = _timeSkew.asSharedFlow()

    private val _conflictDetected = MutableSharedFlow<ConflictInfo>(extraBufferCapacity = 1)
    val conflictDetected: SharedFlow<ConflictInfo> = _conflictDetected.asSharedFlow()

    fun notifySessionExpired() { _sessionExpired.tryEmit(true) }
    fun notifyRateLimit(retryAfter: Int) { _rateLimited.tryEmit(retryAfter) }
    fun notifyTimeSkew(msg: String) { _timeSkew.tryEmit(msg) }
    fun notifyConflict(version: Int?, updatedAt: String?) {
        _conflictDetected.tryEmit(ConflictInfo(version, updatedAt))
    }
}

data class ConflictInfo(val version: Int?, val updatedAt: String?)
