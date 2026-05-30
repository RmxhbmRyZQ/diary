package com.secretdiary.app.data.local.dao

import androidx.room.*
import com.secretdiary.app.data.local.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {

    @Query("SELECT * FROM sync_meta WHERE `key` = :key")
    suspend fun getByKey(key: String): SyncMetaEntity?

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    fun observeValue(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
