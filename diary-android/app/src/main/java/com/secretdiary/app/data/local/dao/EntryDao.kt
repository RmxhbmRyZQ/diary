package com.secretdiary.app.data.local.dao

import androidx.room.*
import com.secretdiary.app.data.local.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE user_id = :userId ORDER BY diary_date DESC")
    fun observeAll(userId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE user_id = :userId ORDER BY diary_date DESC")
    suspend fun getAll(userId: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE user_id = :userId AND is_dirty = 1 ORDER BY local_updated_at ASC")
    suspend fun getDirtyEntries(userId: String): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE user_id = :userId AND id = :id")
    suspend fun getById(userId: String, id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE user_id = :userId AND diary_date = :diaryDate")
    suspend fun getByDiaryDate(userId: String, diaryDate: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE user_id = :userId AND mood = :mood ORDER BY diary_date DESC")
    fun observeByMood(userId: String, mood: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE user_id = :userId AND weather = :weather ORDER BY diary_date DESC")
    fun observeByWeather(userId: String, weather: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE user_id = :userId AND favorite = 1 ORDER BY diary_date DESC")
    fun observeFavorites(userId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE user_id = :userId AND title LIKE '%' || :query || '%' ORDER BY diary_date DESC")
    fun searchByTitle(userId: String, query: String): Flow<List<EntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE user_id = :userId AND id = :id")
    suspend fun deleteById(userId: String, id: String)

    @Query("UPDATE entries SET is_dirty = :isDirty WHERE user_id = :userId AND id = :id")
    suspend fun updateDirty(userId: String, id: String, isDirty: Boolean)

    @Query("""
        UPDATE entries
        SET server_version = :version, server_updated_at = :updatedAt, is_dirty = 0
        WHERE user_id = :userId AND id = :id
    """)
    suspend fun updateSyncMeta(userId: String, id: String, version: Int, updatedAt: String)

    @Query("SELECT COUNT(*) FROM entries WHERE user_id = :userId")
    suspend fun count(userId: String): Int

    @Query("SELECT id FROM entries WHERE user_id = :userId")
    suspend fun getAllIds(userId: String): List<String>
}
