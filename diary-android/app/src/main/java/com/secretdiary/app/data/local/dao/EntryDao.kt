package com.secretdiary.app.data.local.dao

import androidx.room.*
import com.secretdiary.app.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    /** 按日期降序观察所有日记 */
    @Query("SELECT * FROM entries ORDER BY diary_date DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    /** 按日期降序获取所有日记（一次性） */
    @Query("SELECT * FROM entries ORDER BY diary_date DESC")
    suspend fun getAll(): List<EntryEntity>

    /** 获取所有 is_dirty=1 的日记，按 local_updated_at 升序 */
    @Query("SELECT * FROM entries WHERE is_dirty = 1 ORDER BY local_updated_at ASC")
    suspend fun getDirtyEntries(): List<EntryEntity>

    /** 根据 ID 获取 */
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    /** 根据 diaryDate 获取 */
    @Query("SELECT * FROM entries WHERE diary_date = :diaryDate")
    suspend fun getByDiaryDate(diaryDate: String): EntryEntity?

    /** 按心情过滤，日期降序 */
    @Query("SELECT * FROM entries WHERE mood = :mood ORDER BY diary_date DESC")
    fun observeByMood(mood: String): Flow<List<EntryEntity>>

    /** 按天气过滤 */
    @Query("SELECT * FROM entries WHERE weather = :weather ORDER BY diary_date DESC")
    fun observeByWeather(weather: String): Flow<List<EntryEntity>>

    /** 仅收藏 */
    @Query("SELECT * FROM entries WHERE favorite = 1 ORDER BY diary_date DESC")
    fun observeFavorites(): Flow<List<EntryEntity>>

    /** 标题模糊搜索 */
    @Query("SELECT * FROM entries WHERE title LIKE '%' || :query || '%' ORDER BY diary_date DESC")
    fun searchByTitle(query: String): Flow<List<EntryEntity>>

    /** 插入或替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    /** 批量插入/替换 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<EntryEntity>)

    /** 删除 */
    @Delete
    suspend fun delete(entry: EntryEntity)

    /** 根据 ID 删除 */
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 更新 dirty 标记 */
    @Query("UPDATE entries SET is_dirty = :isDirty WHERE id = :id")
    suspend fun updateDirty(id: String, isDirty: Boolean)

    /** 更新 server_version 和 server_updated_at（同步后更新） */
    @Query("""
        UPDATE entries
        SET server_version = :version, server_updated_at = :updatedAt, is_dirty = 0
        WHERE id = :id
    """)
    suspend fun updateSyncMeta(id: String, version: Int, updatedAt: String)

    /** 获取日记总数 */
    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** 获取所有日记 ID */
    @Query("SELECT id FROM entries")
    suspend fun getAllIds(): List<String>
}
