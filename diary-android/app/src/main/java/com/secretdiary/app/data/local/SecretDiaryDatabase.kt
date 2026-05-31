package com.secretdiary.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.secretdiary.app.data.local.dao.AttachmentIvDao
import com.secretdiary.app.data.local.dao.EntryDao
import com.secretdiary.app.data.local.dao.SyncMetaDao
import com.secretdiary.app.data.local.entity.AttachmentIvEntity
import com.secretdiary.app.data.local.entity.EntryEntity
import com.secretdiary.app.data.local.entity.SyncMetaEntity

@Database(
    entities = [
        EntryEntity::class,
        AttachmentIvEntity::class,
        SyncMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SecretDiaryDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun attachmentIvDao(): AttachmentIvDao
    abstract fun syncMetaDao(): SyncMetaDao
}
