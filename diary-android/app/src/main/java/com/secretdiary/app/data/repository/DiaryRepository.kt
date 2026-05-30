package com.secretdiary.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.secretdiary.app.data.local.dao.AttachmentIvDao
import com.secretdiary.app.data.local.dao.EntryDao
import com.secretdiary.app.data.local.dao.SyncMetaDao
import com.secretdiary.app.data.local.entity.AttachmentIvEntity
import com.secretdiary.app.data.local.entity.EntryEntity
import com.secretdiary.app.data.local.entity.SyncMetaEntity
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.*
import com.secretdiary.app.domain.model.Diary
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日记数据仓库：协调本地 Room 与远程 API，处理加解密、is_dirty 标记。
 */
@Singleton
class DiaryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val attachmentIvDao: AttachmentIvDao,
    private val syncMetaDao: SyncMetaDao,
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager
) {
    private val gson = Gson()

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }

    // ---------- 本地查询 ----------

    /** 观察所有日记（解密后），按日期降序 */
    fun observeAllDiaries(): Flow<List<Diary>> =
        entryDao.observeAll().map { entities -> entities.map { it.toDiary() } }

    /** 按心情过滤 */
    fun observeByMood(mood: String): Flow<List<Diary>> =
        entryDao.observeByMood(mood).map { entities -> entities.map { it.toDiary() } }

    /** 按天气过滤 */
    fun observeByWeather(weather: String): Flow<List<Diary>> =
        entryDao.observeByWeather(weather).map { entities -> entities.map { it.toDiary() } }

    /** 仅收藏 */
    fun observeFavorites(): Flow<List<Diary>> =
        entryDao.observeFavorites().map { entities -> entities.map { it.toDiary() } }

    /** 搜索 */
    fun search(query: String): Flow<List<Diary>> =
        entryDao.searchByTitle(query).map { entities -> entities.map { it.toDiary() } }

    /** 根据 ID 获取单篇日记 */
    suspend fun getById(id: String): Diary? = entryDao.getById(id)?.toDiary()

    /** 根据日期获取日记 */
    suspend fun getByDiaryDate(diaryDate: String): Diary? =
        entryDao.getByDiaryDate(diaryDate)?.toDiary()

    /** 获取所有 is_dirty 的日记 */
    suspend fun getDirtyEntries(): List<Diary> =
        entryDao.getDirtyEntries().map { it.toDiary() }

    // ---------- 写入 ----------

    /**
     * 保存日记到本地（加密后存储）。
     * 若网络可用则同时上传；否则标记 is_dirty。
     */
    suspend fun saveDiary(
        id: String,
        diaryDate: String,
        title: String,
        content: String,
        tags: List<String>,
        mood: String?,
        weather: String?,
        favorite: Boolean,
        attachmentIds: List<String>,
        isDirty: Boolean = false
    ): Result<Diary> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val now = TimeUtils.nowBeijingIso()

        // 构造 payload
        val payload = gson.toJson(mapOf(
            "title" to title,
            "content" to content,
            "tags" to tags,
            "attachmentIds" to attachmentIds
        ))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)
        val summary = content.take(50).replace("\n", " ")

        val entity = EntryEntity(
            id = id,
            diaryDate = diaryDate,
            title = title,
            content = content,
            summary = summary,
            tags = gson.toJson(tags),
            mood = mood,
            weather = weather,
            favorite = favorite,
            attachmentIds = gson.toJson(attachmentIds),
            serverVersion = null,
            serverUpdatedAt = null,
            localUpdatedAt = now,
            isDirty = isDirty
        )
        entryDao.upsert(entity)

        // 尝试远程保存
        if (!isDirty) {
            return try {
                val response = apiService.createEntry(
                    CreateEntryRequest(
                        diaryDate = diaryDate,
                        mood = mood,
                        weather = weather,
                        favorite = favorite,
                        encryptedPayload = encryptedPayload,
                        iv = iv,
                        attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                    )
                )
                if (response.isSuccessful && response.body()?.code == 0) {
                    val data = response.body()!!.data!!
                    // 服务端可能返回不同的 ID，需同步到本地
                    val serverId = data.id
                    if (serverId != id) {
                        entryDao.deleteById(id)
                        val synced = entity.copy(id = serverId)
                        entryDao.upsert(synced)
                        entryDao.updateSyncMeta(serverId, data.version, data.updatedAt)
                        Result.success(entryDao.getById(serverId)!!.toDiary())
                    } else {
                        entryDao.updateSyncMeta(id, data.version, data.updatedAt)
                        val updated = entryDao.getById(id) ?: entity
                        Result.success(updated.toDiary())
                    }
                } else {
                    // 回落本地保存，但向 UI 报告失败
                    entryDao.updateDirty(id, true)
                    val msg = response.body()?.message ?: "保存失败 (${response.code()})"
                    Result.failure(Exception(msg))
                }
            } catch (e: Exception) {
                entryDao.updateDirty(id, true)
                Result.failure(e)
            }
        }
        return Result.success(entity.toDiary())
    }

    /**
     * 更新已有日记（全量）。
     */
    suspend fun updateDiary(
        id: String,
        diaryDate: String,
        title: String,
        content: String,
        tags: List<String>,
        mood: String?,
        weather: String?,
        favorite: Boolean,
        attachmentIds: List<String>,
        serverVersion: Int?
    ): Result<Diary> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val now = TimeUtils.nowBeijingIso()

        val payload = gson.toJson(mapOf(
            "title" to title,
            "content" to content,
            "tags" to tags,
            "attachmentIds" to attachmentIds
        ))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)
        val summary = content.take(50).replace("\n", " ")

        val existing = entryDao.getById(id)

        val entity = EntryEntity(
            id = id,
            diaryDate = diaryDate,
            title = title,
            content = content,
            summary = summary,
            tags = gson.toJson(tags),
            mood = mood,
            weather = weather,
            favorite = favorite,
            attachmentIds = gson.toJson(attachmentIds),
            serverVersion = existing?.serverVersion,
            serverUpdatedAt = existing?.serverUpdatedAt,
            localUpdatedAt = now,
            isDirty = true // 先标记 dirty
        )
        entryDao.upsert(entity)

        // 尝试远程更新
        return try {
            val response = apiService.updateEntry(
                id = id,
                request = UpdateEntryRequest(
                    diaryDate = diaryDate,
                    mood = mood,
                    weather = weather,
                    favorite = favorite,
                    encryptedPayload = encryptedPayload,
                    iv = iv,
                    version = serverVersion ?: 1,
                    attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                )
            )
            if (response.isSuccessful && response.body()?.code == 0) {
                val data = response.body()!!.data!!
                entryDao.updateSyncMeta(id, data.version, data.updatedAt)
                Result.success(entryDao.getById(id)!!.toDiary())
            } else {
                val msg = response.body()?.message ?: "保存失败 (${response.code()})"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            entryDao.updateDirty(id, true)
            Result.failure(e)
        }
    }

    /** 更新元数据 */
    suspend fun updateMeta(
        id: String,
        mood: String?,
        weather: String?,
        favorite: Boolean?,
        diaryDate: String?,
        serverVersion: Int?
    ): Result<Diary> {
        val now = TimeUtils.nowBeijingIso()
        val entity = entryDao.getById(id) ?: return Result.failure(Exception("Diary not found"))

        val response = apiService.updateEntryMeta(
            id = id,
            request = UpdateMetaRequest(
                mood = mood,
                weather = weather,
                favorite = favorite,
                diaryDate = diaryDate,
                version = serverVersion ?: 1
            )
        )
        if (response.isSuccessful && response.body()?.code == 0) {
            val data = response.body()!!.data!!
            val updated = entity.copy(
                mood = mood ?: entity.mood,
                weather = weather ?: entity.weather,
                favorite = favorite ?: entity.favorite,
                diaryDate = diaryDate ?: entity.diaryDate,
                serverVersion = data.version,
                serverUpdatedAt = data.updatedAt,
                localUpdatedAt = now,
                isDirty = false
            )
            entryDao.upsert(updated)
            return Result.success(updated.toDiary())
        } else {
            return Result.failure(Exception("Failed to update meta"))
        }
    }

    /** 删除日记 */
    suspend fun deleteDiary(id: String): Result<Unit> {
        entryDao.deleteById(id)
        return try {
            apiService.deleteEntry(id)
            Result.success(Unit)
        } catch (e: Exception) {
            // 本地已删，远程将随同步处理
            Result.success(Unit)
        }
    }

    // ---------- 同步相关 ----------

    /** 获取上次同步时间 */
    suspend fun getLastSyncTimestamp(): String? =
        syncMetaDao.getByKey(KEY_LAST_SYNC)?.value

    /** 更新同步时间 */
    suspend fun updateLastSyncTimestamp(timestamp: String) {
        syncMetaDao.upsert(SyncMetaEntity(KEY_LAST_SYNC, timestamp))
    }

    /** 获取所有本地 ID */
    suspend fun getAllIds(): List<String> = entryDao.getAllIds()

    /** 批量获取 EntryEntity */
    suspend fun getEntriesByIds(ids: List<String>): List<EntryEntity> =
        ids.mapNotNull { entryDao.getById(it) }

    /** 从远程批量获取并解密存储 */
    suspend fun fetchAndStoreBatch(ids: List<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))

        val response = apiService.getBatchEntries(ids.joinToString(","))
        if (response.isSuccessful && response.body()?.code == 0) {
            val entries = response.body()!!.data!!.entries
            val entities = entries.map { remote ->
                val decryptedBytes = cryptoManager.decrypt(remote.encryptedPayload, remote.iv, dek)
                val json = String(decryptedBytes, Charsets.UTF_8)
                @Suppress("UNCHECKED_CAST")
                val map: Map<String, Any> = gson.fromJson(json, Map::class.java) as Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val tags: List<String> = (map["tags"] as? List<String>) ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val attIds: List<String> = (map["attachmentIds"] as? List<String>) ?: emptyList()

                EntryEntity(
                    id = remote.id,
                    diaryDate = remote.diaryDate,
                    title = map["title"] as? String ?: "",
                    content = map["content"] as? String ?: "",
                    summary = (map["content"] as? String ?: "").take(50).replace("\n", " "),
                    tags = gson.toJson(tags),
                    mood = remote.mood,
                    weather = remote.weather,
                    favorite = remote.favorite,
                    attachmentIds = gson.toJson(attIds),
                    serverVersion = remote.version,
                    serverUpdatedAt = remote.updatedAt,
                    localUpdatedAt = TimeUtils.nowBeijingIso(),
                    isDirty = false
                )
            }
            entryDao.upsertAll(entities)
            return Result.success(Unit)
        } else {
            return Result.failure(Exception("Batch fetch failed: ${response.code()}"))
        }
    }

    /** 上传脏日记 */
    suspend fun uploadDirtyEntry(entry: EntryEntity): Result<Unit> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))

        val tags: List<String> = try {
            gson.fromJson(entry.tags, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) { emptyList() }
        val attIds: List<String> = try {
            gson.fromJson(entry.attachmentIds, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) { emptyList() }

        val payload = gson.toJson(mapOf(
            "title" to entry.title,
            "content" to entry.content,
            "tags" to tags,
            "attachmentIds" to attIds
        ))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)

        val response = if (entry.serverVersion != null) {
            apiService.updateEntry(
                id = entry.id,
                request = UpdateEntryRequest(
                    diaryDate = entry.diaryDate,
                    mood = entry.mood,
                    weather = entry.weather,
                    favorite = entry.favorite,
                    encryptedPayload = encryptedPayload,
                    iv = iv,
                    version = entry.serverVersion,
                    attachmentIds = attIds.takeIf { it.isNotEmpty() }
                )
            )
        } else {
            apiService.createEntry(
                CreateEntryRequest(
                    diaryDate = entry.diaryDate,
                    mood = entry.mood,
                    weather = entry.weather,
                    favorite = entry.favorite,
                    encryptedPayload = encryptedPayload,
                    iv = iv,
                    attachmentIds = attIds.takeIf { it.isNotEmpty() }
                )
            )
        }

        return if (response.isSuccessful && response.body()?.code == 0) {
            val data = response.body()!!.data!!
            entryDao.updateSyncMeta(entry.id, data.version, data.updatedAt)
            Result.success(Unit)
        } else {
            val msg = response.body()?.message ?: "Upload failed: ${response.code()}"
            Result.failure(Exception(msg))
        }
    }

    /** 删除不存在于远程摘要列表的本地条目（首次全量同步时使用） */
    suspend fun deleteLocalNotIn(remoteIds: Set<String>) {
        val localIds = entryDao.getAllIds().toSet()
        val toDelete = localIds - remoteIds
        for (id in toDelete) {
            entryDao.deleteById(id)
        }
    }

    /** 存储附件 IV */
    suspend fun saveAttachmentIv(attachmentId: String, ivB64: String) {
        attachmentIvDao.upsert(AttachmentIvEntity(attachmentId, ivB64))
    }

    /** 获取附件 IV */
    suspend fun getAttachmentIv(attachmentId: String): String? =
        attachmentIvDao.getById(attachmentId)?.ivB64

    // -------------------- 私有 --------------------

    private fun getDEK(): SecretKey? = sessionManager.getActiveDEK()

    // -------------------- 实体→领域模型 --------------------

    private fun EntryEntity.toDiary(): Diary {
        val tags: List<String> = try {
            gson.fromJson(this.tags, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) { emptyList() }
        val attIds: List<String> = try {
            gson.fromJson(this.attachmentIds, object : TypeToken<List<String>>() {}.type)
        } catch (_: Exception) { emptyList() }

        return Diary(
            id = id,
            diaryDate = diaryDate,
            title = title,
            content = content,
            summary = summary,
            tags = tags,
            mood = mood,
            weather = weather,
            favorite = favorite,
            attachmentIds = attIds,
            serverVersion = serverVersion,
            serverUpdatedAt = serverUpdatedAt,
            localUpdatedAt = localUpdatedAt,
            isDirty = isDirty
        )
    }
}
