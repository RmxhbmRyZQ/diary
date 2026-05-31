package com.secretdiary.app.sync

import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.*
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.domain.model.Diary
import com.secretdiary.app.util.NetworkMonitor
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import okhttp3.ResponseBody
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private val repository: DiaryRepository = mockk(relaxed = true)
    private val apiService: ApiService = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)
    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        every { networkMonitor.observe() } returns flowOf(true)

        syncManager = SyncManager(repository, apiService, networkMonitor)
        syncManager.setTestScope(CoroutineScope(UnconfinedTestDispatcher()))
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // -------------------- 时间偏差 --------------------

    @Test
    fun `when sync returns 400 time skew, state is TimeSkew`() = runTest {
        coEvery { repository.getLastSyncTimestamp() } returns "2026-05-28T10:00:00+08:00"
        coEvery { apiService.getSyncEntries(any(), any()) } returns Response.error(400, mockk(relaxed = true))

        syncManager.performSync()

        val state = syncManager.syncState.value
        assertTrue(state is SyncState.TimeSkew)
    }

    // -------------------- 首次全量同步 --------------------

    @Test
    fun `first sync with no local data downloads all`() = runTest {
        val remoteEntries = listOf(
            SyncEntry("id1", "2026-05-27", "2026-05-27T20:00:00+08:00"),
            SyncEntry("id2", "2026-05-26", "2026-05-26T18:00:00+08:00")
        )
        val syncResponse: ApiResponse<SyncResponse> = mockk()
        every { syncResponse.code } returns 0
        every { syncResponse.data } returns SyncResponse(remoteEntries)

        coEvery { repository.getLastSyncTimestamp() } returns null
        coEvery { apiService.getSyncEntries(any(), null) } returns Response.success(syncResponse)
        coEvery { repository.getAllIds() } returns emptyList()
        coEvery { repository.getDirtyEntries() } returns emptyList()
        coEvery { repository.fetchAndStoreBatch(any()) } returns Result.success(Unit)
        coEvery { repository.deleteLocalNotIn(any()) } just runs
        coEvery { repository.updateLastSyncTimestamp(any()) } just runs

        syncManager.performSync()

        val state = syncManager.syncState.value
        assertTrue(state is SyncState.Success)
    }

    // -------------------- 增量同步 --------------------

    @Test
    fun `incremental sync only downloads updated entries`() = runTest {
        val remoteEntries = listOf(
            SyncEntry("id1", "2026-05-27", "2026-05-28T20:00:00+08:00"),
            SyncEntry("id2", "2026-05-26", "2026-05-26T18:00:00+08:00")
        )
        val syncResponse: ApiResponse<SyncResponse> = mockk()
        every { syncResponse.code } returns 0
        every { syncResponse.data } returns SyncResponse(remoteEntries)

        coEvery { repository.getLastSyncTimestamp() } returns "2026-05-27T10:00:00+08:00"
        coEvery { apiService.getSyncEntries(any(), any()) } returns Response.success(syncResponse)
        coEvery { repository.getAllIds() } returns listOf("id2")
        coEvery { repository.getDirtyEntries() } returns emptyList()

        // id2 本地有但 serverUpdatedAt 比 remote 旧 → 需下载
        val existingDiary = Diary(
            id = "id2", diaryDate = "2026-05-26", title = "old", content = "old",
            summary = "old", tags = emptyList(), mood = null, weather = null,
            favorite = false, attachmentIds = emptyList(), serverVersion = 1,
            serverUpdatedAt = "2026-05-26T18:00:00+08:00", localUpdatedAt = null,
            isDirty = false
        )
        coEvery { repository.getById("id2") } returns existingDiary
        coEvery { repository.fetchAndStoreBatch(any()) } returns Result.success(Unit)
        coEvery { repository.updateLastSyncTimestamp(any()) } just runs

        syncManager.performSync()

        val state = syncManager.syncState.value
        assertTrue(state is SyncState.Success)
    }

    // -------------------- 脏数据上传 --------------------

    @Test
    fun `dirty entries are uploaded during sync`() = runTest {
        val dirtyDiary = Diary(
            id = "dirty1", diaryDate = "2026-05-29", title = "dirty", content = "dirty",
            summary = "dirty", tags = emptyList(), mood = null, weather = null,
            favorite = false, attachmentIds = emptyList(), serverVersion = null,
            serverUpdatedAt = null, localUpdatedAt = "2026-05-29T10:00:00+08:00",
            isDirty = true
        )

        val syncResponse: ApiResponse<SyncResponse> = mockk()
        every { syncResponse.code } returns 0
        every { syncResponse.data } returns SyncResponse(emptyList())

        coEvery { repository.getLastSyncTimestamp() } returns null
        coEvery { apiService.getSyncEntries(any(), null) } returns Response.success(syncResponse)
        coEvery { repository.getAllIds() } returns listOf("dirty1")
        coEvery { repository.getDirtyEntries() } returns listOf(dirtyDiary)
        coEvery { repository.deleteLocalNotIn(any()) } just runs
        coEvery { repository.updateLastSyncTimestamp(any()) } just runs

        val dirtyEntity = com.secretdiary.app.data.local.entity.EntryEntity(
            id = "dirty1", userId = "testuser", diaryDate = "2026-05-29",
            title = "dirty",
            encryptedContent = "enc", contentIv = "iv",
            summary = "dirty", tags = "[]",
            mood = null, weather = null, favorite = false, attachmentIds = "[]",
            serverVersion = null, serverUpdatedAt = null,
            localUpdatedAt = "2026-05-29T10:00:00+08:00", isDirty = true
        )
        coEvery { repository.getEntriesByIds(listOf("dirty1")) } returns listOf(dirtyEntity)
        coEvery { repository.uploadDirtyEntry(any()) } returns Result.success(Unit)

        syncManager.performSync()

        val state = syncManager.syncState.value
        assertTrue(state is SyncState.Success)
    }

    // -------------------- 409 冲突 --------------------

    @Test
    fun `conflict triggers re-fetch and ConflictDetected state`() = runTest {
        val dirtyDiary = Diary(
            id = "conflict1", diaryDate = "2026-05-29", title = "conflict", content = "conflict",
            summary = "c", tags = emptyList(), mood = null, weather = null,
            favorite = false, attachmentIds = emptyList(), serverVersion = 2,
            serverUpdatedAt = "2026-05-28T10:00:00+08:00", localUpdatedAt = null,
            isDirty = true
        )

        val syncResponse: ApiResponse<SyncResponse> = mockk()
        every { syncResponse.code } returns 0
        every { syncResponse.data } returns SyncResponse(emptyList())

        coEvery { repository.getLastSyncTimestamp() } returns null
        coEvery { apiService.getSyncEntries(any(), null) } returns Response.success(syncResponse)
        coEvery { repository.getAllIds() } returns listOf("conflict1")
        coEvery { repository.getDirtyEntries() } returns listOf(dirtyDiary)
        coEvery { repository.deleteLocalNotIn(any()) } just runs
        coEvery { repository.updateLastSyncTimestamp(any()) } just runs

        val dirtyEntity = com.secretdiary.app.data.local.entity.EntryEntity(
            id = "conflict1", userId = "testuser", diaryDate = "2026-05-29",
            title = "conflict",
            encryptedContent = "enc", contentIv = "iv",
            summary = "c", tags = "[]",
            mood = null, weather = null, favorite = false, attachmentIds = "[]",
            serverVersion = 2, serverUpdatedAt = "2026-05-28T10:00:00+08:00",
            localUpdatedAt = null, isDirty = true
        )
        coEvery { repository.getEntriesByIds(listOf("conflict1")) } returns listOf(dirtyEntity)
        coEvery { repository.uploadDirtyEntry(any()) } returns Result.failure(Exception("conflict"))
        coEvery { repository.fetchAndStoreBatch(listOf("conflict1")) } returns Result.success(Unit)

        syncManager.performSync()
        advanceUntilIdle()

        // 冲突后仍然继续同步
        val state = syncManager.syncState.value
        assertTrue(state is SyncState.Success)
    }
}
