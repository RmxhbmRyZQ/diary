package com.secretdiary.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 预留：后续版本升级时在此添加迁移逻辑
            }
        }
    }
}
