package com.docly.app.app.di

import android.content.Context
import androidx.room.Room
import com.docly.app.data.local.dao.ConversionJobDao
import com.docly.app.data.local.dao.DiagnosticEventDao
import com.docly.app.data.local.dao.DocumentDao
import com.docly.app.data.local.dao.FolderDao
import com.docly.app.data.local.dao.RecentDocumentDao
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
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideDocumentDao(database: AppDatabase): DocumentDao = database.documentDao()

    @Provides
    fun provideFolderDao(database: AppDatabase): FolderDao = database.folderDao()

    @Provides
    fun provideRecentDocumentDao(database: AppDatabase): RecentDocumentDao = database.recentDocumentDao()

    @Provides
    fun provideConversionJobDao(database: AppDatabase): ConversionJobDao = database.conversionJobDao()

    @Provides
    fun provideScanSessionDao(database: AppDatabase): ScanSessionDao = database.scanSessionDao()

    @Provides
    fun provideScannedPageDao(database: AppDatabase): ScannedPageDao = database.scannedPageDao()

    @Provides
    fun provideDiagnosticEventDao(database: AppDatabase): DiagnosticEventDao = database.diagnosticEventDao()

    private const val DATABASE_NAME = "docly.db"
}
