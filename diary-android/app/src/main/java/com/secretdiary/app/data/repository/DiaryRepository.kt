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
        private const val KEY_LAST_SYNC_PREFIX = "last_sync_timestamp"
    }

    private fun syncMetaKey(): String = "${KEY_LAST_SYNC_PREFIX}_${currentUserId()}"
    private fun currentUserId(): String = sessionManager.getUsername() ?: ""

    // ---------- 本地查询 ----------

    fun observeAllDiaries(): Flow<List<Diary>> =
        entryDao.observeAll(currentUserId()).map { entities -> entities.map { it.toDiary() } }

    fun observeByMood(mood: String): Flow<List<Diary>> =
        entryDao.observeByMood(currentUserId(), mood).map { entities -> entities.map { it.toDiary() } }

    fun observeByWeather(weather: String): Flow<List<Diary>> =
        entryDao.observeByWeather(currentUserId(), weather).map { entities -> entities.map { it.toDiary() } }

    fun observeFavorites(): Flow<List<Diary>> =
        entryDao.observeFavorites(currentUserId()).map { entities -> entities.map { it.toDiary() } }

    fun search(query: String): Flow<List<Diary>> =
        entryDao.searchByTitle(currentUserId(), query).map { entities -> entities.map { it.toDiary() } }

    suspend fun getById(id: String): Diary? = entryDao.getById(currentUserId(), id)?.toDiary()

    suspend fun getByDiaryDate(diaryDate: String): Diary? =
        entryDao.getByDiaryDate(currentUserId(), diaryDate)?.toDiary()

    suspend fun getDirtyEntries(): List<Diary> =
        entryDao.getDirtyEntries(currentUserId()).map { it.toDiary() }

    // ---------- 写入 ----------

    suspend fun saveDiary(
        id: String, diaryDate: String, title: String, content: String,
        tags: List<String>, mood: String?, weather: String?, favorite: Boolean,
        attachmentIds: List<String>, isDirty: Boolean = false
    ): Result<Diary> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val now = TimeUtils.nowBeijingIso()
        val userId = currentUserId()

        val payload = gson.toJson(mapOf("title" to title, "content" to content, "tags" to tags, "attachmentIds" to attachmentIds))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)
        val summary = content.take(50).replace("\n", " ")
        val (encContent, contentIv) = encryptContent(content, dek)

        val entity = EntryEntity(
            id = id, userId = userId, diaryDate = diaryDate, title = title,
            encryptedContent = encContent, contentIv = contentIv, summary = summary,
            tags = gson.toJson(tags), mood = mood, weather = weather, favorite = favorite,
            attachmentIds = gson.toJson(attachmentIds),
            serverVersion = null, serverUpdatedAt = null,
            localUpdatedAt = now, isDirty = isDirty
        )
        entryDao.upsert(entity)

        if (!isDirty) {
            return try {
                val response = apiService.createEntry(CreateEntryRequest(
                    diaryDate = diaryDate, mood = mood, weather = weather, favorite = favorite,
                    encryptedPayload = encryptedPayload, iv = iv,
                    attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                ))
                if (response.isSuccessful && response.body()?.code == 0) {
                    val data = response.body()!!.data!!
                    val serverId = data.id
                    if (serverId != id) {
                        entryDao.deleteById(userId, id)
                        entryDao.upsert(entity.copy(id = serverId))
                        entryDao.updateSyncMeta(userId, serverId, data.version, data.updatedAt)
                        Result.success(entryDao.getById(userId, serverId)!!.toDiary())
                    } else {
                        entryDao.updateSyncMeta(userId, id, data.version, data.updatedAt)
                        Result.success(entryDao.getById(userId, id)!!.toDiary())
                    }
                } else {
                    entryDao.updateDirty(userId, id, true)
                    Result.failure(Exception(response.body()?.message ?: "保存失败 (${response.code()})"))
                }
            } catch (e: Exception) {
                entryDao.updateDirty(userId, id, true)
                Result.failure(e)
            }
        }
        return Result.success(entity.toDiary())
    }

    suspend fun updateDiary(
        id: String, diaryDate: String, title: String, content: String,
        tags: List<String>, mood: String?, weather: String?, favorite: Boolean,
        attachmentIds: List<String>
    ): Result<Diary> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val now = TimeUtils.nowBeijingIso()
        val userId = currentUserId()

        val payload = gson.toJson(mapOf("title" to title, "content" to content, "tags" to tags, "attachmentIds" to attachmentIds))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)
        val summary = content.take(50).replace("\n", " ")
        val (encContent, contentIv) = encryptContent(content, dek)

        val existing = entryDao.getById(userId, id)
        val entity = EntryEntity(
            id = id, userId = userId, diaryDate = diaryDate, title = title,
            encryptedContent = encContent, contentIv = contentIv, summary = summary,
            tags = gson.toJson(tags), mood = mood, weather = weather, favorite = favorite,
            attachmentIds = gson.toJson(attachmentIds),
            serverVersion = existing?.serverVersion, serverUpdatedAt = existing?.serverUpdatedAt,
            localUpdatedAt = now, isDirty = true
        )
        entryDao.upsert(entity)

        return try {
            val response = if (existing?.serverVersion != null) {
                apiService.updateEntry(id, UpdateEntryRequest(
                    diaryDate = diaryDate, mood = mood, weather = weather, favorite = favorite,
                    encryptedPayload = encryptedPayload, iv = iv, version = existing.serverVersion,
                    attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                ))
            } else {
                apiService.createEntry(CreateEntryRequest(
                    diaryDate = diaryDate, mood = mood, weather = weather, favorite = favorite,
                    encryptedPayload = encryptedPayload, iv = iv,
                    attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                ))
            }
            if (response.isSuccessful && response.body()?.code == 0) {
                val data = response.body()!!.data!!
                if (existing?.serverVersion == null) {
                    val serverId = data.id
                    if (serverId != id) entryDao.deleteById(userId, id)
                    entryDao.updateSyncMeta(userId, serverId, data.version, data.updatedAt)
                } else {
                    entryDao.updateSyncMeta(userId, id, data.version, data.updatedAt)
                }
                Result.success(entryDao.getById(userId, data.id)!!.toDiary())
            } else {
                Result.failure(Exception(response.body()?.message ?: "保存失败 (${response.code()})"))
            }
        } catch (e: Exception) {
            entryDao.updateDirty(userId, id, true)
            Result.failure(e)
        }
    }

    suspend fun updateMeta(
        id: String, mood: String?, weather: String?, favorite: Boolean?,
        diaryDate: String?, serverVersion: Int?
    ): Result<Diary> {
        val userId = currentUserId()
        val now = TimeUtils.nowBeijingIso()
        val entity = entryDao.getById(userId, id) ?: return Result.failure(Exception("Diary not found"))
        val response = apiService.updateEntryMeta(id, UpdateMetaRequest(
            mood = mood, weather = weather, favorite = favorite,
            diaryDate = diaryDate, version = serverVersion ?: 1
        ))
        return if (response.isSuccessful && response.body()?.code == 0) {
            val data = response.body()!!.data!!
            val updated = entity.copy(
                mood = mood ?: entity.mood, weather = weather ?: entity.weather,
                favorite = favorite ?: entity.favorite, diaryDate = diaryDate ?: entity.diaryDate,
                serverVersion = data.version, serverUpdatedAt = data.updatedAt,
                localUpdatedAt = now, isDirty = false
            )
            entryDao.upsert(updated)
            Result.success(updated.toDiary())
        } else {
            Result.failure(Exception("Failed to update meta"))
        }
    }

    suspend fun deleteDiary(id: String): Result<Unit> {
        entryDao.deleteById(currentUserId(), id)
        return try { apiService.deleteEntry(id); Result.success(Unit) } catch (_: Exception) { Result.success(Unit) }
    }

    // ---------- 同步相关 ----------

    suspend fun getLastSyncTimestamp(): String? = syncMetaDao.getByKey(syncMetaKey())?.value

    suspend fun updateLastSyncTimestamp(timestamp: String) {
        syncMetaDao.upsert(SyncMetaEntity(syncMetaKey(), timestamp))
    }

    suspend fun getAllIds(): List<String> = entryDao.getAllIds(currentUserId())

    suspend fun getEntriesByIds(ids: List<String>): List<EntryEntity> {
        val userId = currentUserId()
        return ids.mapNotNull { entryDao.getById(userId, it) }
    }

    suspend fun fetchAndStoreBatch(ids: List<String>): Result<Unit> {
        if (ids.isEmpty()) return Result.success(Unit)
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val userId = currentUserId()

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
                val content = map["content"] as? String ?: ""
                val (encContent, contentIv) = encryptContent(content, dek)

                EntryEntity(
                    id = remote.id, userId = userId, diaryDate = remote.diaryDate,
                    title = map["title"] as? String ?: "",
                    encryptedContent = encContent, contentIv = contentIv,
                    summary = content.take(50).replace("\n", " "),
                    tags = gson.toJson(tags), mood = remote.mood, weather = remote.weather,
                    favorite = remote.favorite, attachmentIds = gson.toJson(attIds),
                    serverVersion = remote.version, serverUpdatedAt = remote.updatedAt,
                    localUpdatedAt = TimeUtils.nowBeijingIso(), isDirty = false
                )
            }
            entryDao.upsertAll(entities)
            return Result.success(Unit)
        }
        return Result.failure(Exception("Batch fetch failed: ${response.code()}"))
    }

    suspend fun uploadDirtyEntry(entry: EntryEntity): Result<Unit> {
        val dek = getDEK() ?: return Result.failure(IllegalStateException("DEK not available"))
        val userId = currentUserId()

        val tags: List<String> = try { gson.fromJson(entry.tags, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) { emptyList() }
        val attIds: List<String> = try { gson.fromJson(entry.attachmentIds, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) { emptyList() }
        val content = decryptContent(entry.encryptedContent, entry.contentIv, dek)

        val payload = gson.toJson(mapOf("title" to entry.title, "content" to content, "tags" to tags, "attachmentIds" to attIds))
        val (encryptedPayload, iv) = cryptoManager.encrypt(payload.toByteArray(Charsets.UTF_8), dek)

        val response = if (entry.serverVersion != null) {
            apiService.updateEntry(entry.id, UpdateEntryRequest(
                diaryDate = entry.diaryDate, mood = entry.mood, weather = entry.weather,
                favorite = entry.favorite, encryptedPayload = encryptedPayload, iv = iv,
                version = entry.serverVersion,
                attachmentIds = attIds.takeIf { it.isNotEmpty() }
            ))
        } else {
            apiService.createEntry(CreateEntryRequest(
                diaryDate = entry.diaryDate, mood = entry.mood, weather = entry.weather,
                favorite = entry.favorite, encryptedPayload = encryptedPayload, iv = iv,
                attachmentIds = attIds.takeIf { it.isNotEmpty() }
            ))
        }
        return if (response.isSuccessful && response.body()?.code == 0) {
            entryDao.updateSyncMeta(userId, entry.id, response.body()!!.data!!.version, response.body()!!.data!!.updatedAt)
            Result.success(Unit)
        } else {
            Result.failure(Exception(response.body()?.message ?: "Upload failed: ${response.code()}"))
        }
    }

    suspend fun deleteLocalNotIn(remoteIds: Set<String>) {
        val localIds = entryDao.getAllIds(currentUserId()).toSet()
        for (id in localIds - remoteIds) entryDao.deleteById(currentUserId(), id)
    }

    suspend fun saveAttachmentIv(attachmentId: String, ivB64: String) {
        attachmentIvDao.upsert(AttachmentIvEntity(attachmentId, ivB64))
    }

    suspend fun getAttachmentIv(attachmentId: String): String? =
        attachmentIvDao.getById(attachmentId)?.ivB64

    // -------------------- 私有 --------------------

    private fun getDEK(): SecretKey? = sessionManager.getActiveDEK()

    private fun encryptContent(content: String, dek: SecretKey): Pair<String, String> {
        val (enc, iv) = cryptoManager.encrypt(content.toByteArray(Charsets.UTF_8), dek)
        return enc to iv
    }

    private fun decryptContent(encryptedContent: String, contentIv: String, dek: SecretKey): String {
        return try {
            val bytes = cryptoManager.decrypt(encryptedContent, contentIv, dek)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            "" // 解密失败（可能数据损坏或 DEK 不匹配），返回空字符串
        }
    }

    private fun EntryEntity.toDiary(): Diary {
        val tags: List<String> = try { gson.fromJson(this.tags, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) { emptyList() }
        val attIds: List<String> = try { gson.fromJson(this.attachmentIds, object : TypeToken<List<String>>() {}.type) } catch (_: Exception) { emptyList() }
        val content = getDEK()?.let { decryptContent(encryptedContent, contentIv, it) } ?: ""

        return Diary(
            id = id, diaryDate = diaryDate, title = title, content = content,
            summary = summary, tags = tags, mood = mood, weather = weather,
            favorite = favorite, attachmentIds = attIds,
            serverVersion = serverVersion, serverUpdatedAt = serverUpdatedAt,
            localUpdatedAt = localUpdatedAt, isDirty = isDirty
        )
    }
}
