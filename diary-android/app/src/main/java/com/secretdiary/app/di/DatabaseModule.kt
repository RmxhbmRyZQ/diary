package com.secretdiary.app.di

import android.content.Context
import androidx.room.Room
import com.secretdiary.app.data.local.SecretDiaryDatabase
import com.secretdiary.app.data.local.dao.AttachmentIvDao
import com.secretdiary.app.data.local.dao.EntryDao
import com.secretdiary.app.data.local.dao.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SecretDiaryDatabase =
        Room.databaseBuilder(context, SecretDiaryDatabase::class.java, "secret_diary.db")
            .addMigrations(SecretDiaryDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideEntryDao(db: SecretDiaryDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideAttachmentIvDao(db: SecretDiaryDatabase): AttachmentIvDao = db.attachmentIvDao()

    @Provides
    fun provideSyncMetaDao(db: SecretDiaryDatabase): SyncMetaDao = db.syncMetaDao()
}
