package com.docly.app.app.di

import android.content.Context
import androidx.room.Room
import com.docly.app.data.local.dao.SavedDocumentDao
import com.docly.app.data.local.dao.ScanSessionDao
import com.docly.app.data.local.dao.ScannedPageDao
import com.docly.app.data.local.db.AppDatabase
import com.docly.app.data.local.db.RoomMigrations
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context = context,
        klass = AppDatabase::class.java,
        name = DATABASE_NAME
    ).addMigrations(*RoomMigrations.ALL)
        .build()

    @Provides
    fun provideScanSessionDao(database: AppDatabase): ScanSessionDao = database.scanSessionDao()

    @Provides
    fun provideScannedPageDao(database: AppDatabase): ScannedPageDao = database.scannedPageDao()

    @Provides
    fun provideSavedDocumentDao(database: AppDatabase): SavedDocumentDao = database.savedDocumentDao()

    private const val DATABASE_NAME = "docly.db"
}
