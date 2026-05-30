package com.secretdiary.app.sync

import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.util.NetworkMonitor
import com.secretdiary.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 增量同步管理器。
 * 严格按照需求文档 3.4.2 节流程实现。
 */
@Singleton
class SyncManager @Inject constructor(
    private val repository: DiaryRepository,
    private val apiService: ApiService,
    private val networkMonitor: NetworkMonitor
) {
    // 可通过 setTestScope 在测试中替换 scope
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 仅供测试使用：替换为测试 dispatcher 的 scope */
    fun setTestScope(testScope: CoroutineScope) { this.scope = testScope }
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    /** 分批大小 */
    companion object {
        private const val BATCH_SIZE = 50
    }

    // 网络恢复后自动触发同步
    private var lastWasOffline = false

    init {
        scope.launch {
            networkMonitor.observe().collect { online ->
                if (online && lastWasOffline) {
                    performSync()
                }
                lastWasOffline = !online
            }
        }
    }

    /**
     * 执行一次完整的增量同步。
     * 调用前确保 DEK 已在 SessionManager 中可用。
     */
    fun performSync() {
        scope.launch {
            _syncState.value = SyncState.InProgress
            try {
                // 1. 获取上次同步时间
                val lastSync = repository.getLastSyncTimestamp()
                val clientTime = TimeUtils.nowBeijingIso()

                // 2. 获取远程变更摘要
                val syncResponse = apiService.getSyncEntries(
                    clientTime = clientTime,
                    since = if (lastSync.isNullOrEmpty()) null else lastSync
                )

                // 处理 400 时间偏差
                if (syncResponse.code() == 400) {
                    _syncState.value = SyncState.TimeSkew("客户端时间与服务器时间偏差过大，请校准系统时间")
                    return@launch
                }

                if (!syncResponse.isSuccessful || syncResponse.body()?.code != 0) {
                    _syncState.value = SyncState.Failed("同步请求失败: ${syncResponse.code()}")
                    return@launch
                }

                val remoteEntries = syncResponse.body()!!.data!!.entries
                val localIds = repository.getAllIds().toSet()
                val remoteIds = remoteEntries.map { it.id }.toSet()

                // 3. 首次全量同步：删除本地多余条目
                if (lastSync.isNullOrEmpty()) {
                    repository.deleteLocalNotIn(remoteIds)
                }

                // 4. 比对决定需下载的 ID
                val toDownload = mutableListOf<String>()
                for (remote in remoteEntries) {
                    if (remote.id !in localIds) {
                        toDownload.add(remote.id)
                    } else {
                        // 本地有，但服务端更新了
                        val local = repository.getById(remote.id)
                        if (local != null && local.serverUpdatedAt != null) {
                            if (remote.updatedAt > local.serverUpdatedAt) {
                                toDownload.add(remote.id)
                            }
                        } else if (local != null && local.serverUpdatedAt == null) {
                            // 本地从未同步过，下载
                            toDownload.add(remote.id)
                        }
                    }
                }

                // 5. 分批下载并存储
                toDownload.chunked(BATCH_SIZE).forEach { batch ->
                    val result = repository.fetchAndStoreBatch(batch)
                    if (result.isFailure) {
                        _syncState.value = SyncState.Failed("批量下载失败: ${result.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                // 6. 上传本地脏日记（按 local_updated_at 升序）
                val dirtyEntries = repository.getDirtyEntries()
                for (dirty in dirtyEntries) {
                    val entity = repository.getEntriesByIds(listOf(dirty.id)).firstOrNull() ?: continue
                    val uploadResult = repository.uploadDirtyEntry(entity)
                    if (uploadResult.isFailure) {
                        // 可能 409 冲突：重新拉取并覆盖本地
                        _syncState.value = SyncState.ConflictDetected(dirty.id)
                        repository.fetchAndStoreBatch(listOf(dirty.id))
                    }
                }

                // 7. 更新最后同步时间
                repository.updateLastSyncTimestamp(clientTime)

                _syncState.value = SyncState.Success
            } catch (e: Exception) {
                _syncState.value = SyncState.Failed(e.message ?: "同步失败")
            }
        }
    }

    /** 重置同步状态 */
    fun resetState() {
        _syncState.value = SyncState.Idle
    }
}
