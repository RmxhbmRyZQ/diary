package com.secretdiary.app.data.local.dao

import androidx.room.*
import com.secretdiary.app.data.local.entity.AttachmentIvEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentIvDao {

    @Query("SELECT * FROM attachment_iv WHERE attachmentId = :attachmentId")
    suspend fun getById(attachmentId: String): AttachmentIvEntity?

    @Query("SELECT * FROM attachment_iv WHERE attachmentId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<AttachmentIvEntity>

    @Query("SELECT * FROM attachment_iv ORDER BY attachmentId DESC")
    fun observeAll(): Flow<List<AttachmentIvEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentIvEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttachmentIvEntity>)

    @Query("DELETE FROM attachment_iv WHERE attachmentId = :attachmentId")
    suspend fun deleteById(attachmentId: String)

    @Query("DELETE FROM attachment_iv WHERE attachmentId IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM attachment_iv")
    suspend fun deleteAll()
}
