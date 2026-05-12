package com.docly.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docly.app.data.local.dao.ConversionJobDao
import com.docly.app.data.local.dao.DiagnosticEventDao
import com.docly.app.data.local.dao.DocumentDao
import com.docly.app.data.local.dao.FolderDao
import com.docly.app.data.local.dao.RecentDocumentDao
import com.docly.app.data.local.dao.ScanSessionDao
import com.docly.app.data.local.dao.ScannedPageDao
import com.docly.app.data.local.entity.ConversionJobEntity
import com.docly.app.data.local.entity.DiagnosticEventEntity
import com.docly.app.data.local.entity.DocumentEntity
import com.docly.app.data.local.entity.FolderEntity
import com.docly.app.data.local.entity.RecentDocumentEntity
import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.data.local.entity.ScannedPageEntity

@Database(
    entities = [
        DocumentEntity::class,
        FolderEntity::class,
        RecentDocumentEntity::class,
        ConversionJobEntity::class,
        ScanSessionEntity::class,
        ScannedPageEntity::class,
        DiagnosticEventEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun folderDao(): FolderDao
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun conversionJobDao(): ConversionJobDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scannedPageDao(): ScannedPageDao
    abstract fun diagnosticEventDao(): DiagnosticEventDao
}
