package com.secretdiary.app.sync

/**
 * 同步状态。
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object InProgress : SyncState()
    data object Success : SyncState()
    data class Failed(val message: String) : SyncState()
    data class TimeSkew(val message: String) : SyncState()
    data class ConflictDetected(val entryId: String) : SyncState()
}
