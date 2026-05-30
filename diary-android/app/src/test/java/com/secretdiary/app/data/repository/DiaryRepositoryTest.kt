package com.secretdiary.app.data.repository

import com.secretdiary.app.data.local.dao.AttachmentIvDao
import com.secretdiary.app.data.local.dao.EntryDao
import com.secretdiary.app.data.local.dao.SyncMetaDao
import com.secretdiary.app.data.local.entity.AttachmentIvEntity
import com.secretdiary.app.data.local.entity.EntryEntity
import com.secretdiary.app.data.local.entity.SyncMetaEntity
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.*
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SessionManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryRepositoryTest {

    private val entryDao: EntryDao = mockk(relaxed = true)
    private val attachmentIvDao: AttachmentIvDao = mockk(relaxed = true)
    private val syncMetaDao: SyncMetaDao = mockk(relaxed = true)
    private val apiService: ApiService = mockk(relaxed = true)
    private val cryptoManager: CryptoManager = mockk()
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private lateinit var repository: DiaryRepository

    @Before
    fun setUp() {
        repository = DiaryRepository(
            entryDao, attachmentIvDao, syncMetaDao,
            apiService, cryptoManager, sessionManager
        )
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // -------------------- 本地查询 --------------------

    @Test
    fun `getById returns diary when entry exists`() = runTest {
        val entity = createTestEntity()
        coEvery { entryDao.getById("id1") } returns entity

        val result = repository.getById("id1")

        assertNotNull(result)
        assertEquals("Test Title", result!!.title)
        assertEquals("Test Content", result.content)
        assertEquals(listOf("tag1"), result.tags)
    }

    @Test
    fun `getById returns null when entry not found`() = runTest {
        coEvery { entryDao.getById("nonexistent") } returns null

        val result = repository.getById("nonexistent")

        assertNull(result)
    }

    @Test
    fun `observeAllDiaries returns flow of diaries`() = runTest {
        val entity = createTestEntity()
        every { entryDao.observeAll() } returns flowOf(listOf(entity))

        repository.observeAllDiaries().collect { diaries ->
            assertEquals(1, diaries.size)
            assertEquals("Test Title", diaries[0].title)
        }
    }

    @Test
    fun `getDirtyEntries returns only dirty entries`() = runTest {
        val dirty = createTestEntity().copy(isDirty = true)
        coEvery { entryDao.getDirtyEntries() } returns listOf(dirty)

        val result = repository.getDirtyEntries()

        assertEquals(1, result.size)
        assertTrue(result[0].isDirty)
    }

    // -------------------- 附件 IV 存储 --------------------

    @Test
    fun `saveAttachmentIv upserts to dao`() = runTest {
        coEvery { attachmentIvDao.upsert(any()) } just runs

        repository.saveAttachmentIv("att1", "ivB64")

        coVerify { attachmentIvDao.upsert(AttachmentIvEntity("att1", "ivB64")) }
    }

    @Test
    fun `getAttachmentIv retrieves from dao`() = runTest {
        coEvery { attachmentIvDao.getById("att1") } returns AttachmentIvEntity("att1", "ivB64")

        val result = repository.getAttachmentIv("att1")

        assertEquals("ivB64", result)
    }

    // -------------------- 同步元数据 --------------------

    @Test
    fun `getLastSyncTimestamp returns null when not stored`() = runTest {
        coEvery { syncMetaDao.getByKey("last_sync_timestamp") } returns null

        val result = repository.getLastSyncTimestamp()

        assertNull(result)
    }

    @Test
    fun `getLastSyncTimestamp returns stored value`() = runTest {
        coEvery { syncMetaDao.getByKey("last_sync_timestamp") } returns
            SyncMetaEntity("last_sync_timestamp", "2026-05-28T10:00:00+08:00")

        val result = repository.getLastSyncTimestamp()

        assertEquals("2026-05-28T10:00:00+08:00", result)
    }

    @Test
    fun `updateLastSyncTimestamp stores new timestamp`() = runTest {
        coEvery { syncMetaDao.upsert(any()) } just runs

        repository.updateLastSyncTimestamp("2026-05-29T12:00:00+08:00")

        coVerify { syncMetaDao.upsert(SyncMetaEntity("last_sync_timestamp", "2026-05-29T12:00:00+08:00")) }
    }

    // -------------------- batch fetch --------------------

    @Test
    fun `fetchAndStoreBatch returns failure when DEK not available`() = runTest {
        every { sessionManager.getActiveDEK() } returns null

        val result = repository.fetchAndStoreBatch(listOf("id1"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetchAndStoreBatch returns success for empty ids`() = runTest {
        val result = repository.fetchAndStoreBatch(emptyList())

        assertTrue(result.isSuccess)
    }

    // -------------------- helpers --------------------

    private fun createTestEntity() = EntryEntity(
        id = "id1",
        diaryDate = "2026-05-28",
        title = "Test Title",
        content = "Test Content",
        summary = "Test Content",
        tags = "[\"tag1\"]",
        mood = "happy",
        weather = null,
        favorite = false,
        attachmentIds = "[]",
        serverVersion = 1,
        serverUpdatedAt = "2026-05-28T10:00:00+08:00",
        localUpdatedAt = "2026-05-28T10:00:00+08:00",
        isDirty = false
    )
}
