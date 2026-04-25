package com.docly.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.docly.app.data.local.dao.SavedDocumentDao
import com.docly.app.data.local.dao.ScanSessionDao
import com.docly.app.data.local.dao.ScannedPageDao
import com.docly.app.data.local.entity.SavedDocumentEntity
import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.data.local.entity.ScannedPageEntity

@Database(
    entities = [
        ScanSessionEntity::class,
        ScannedPageEntity::class,
        SavedDocumentEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun scannedPageDao(): ScannedPageDao
    abstract fun savedDocumentDao(): SavedDocumentDao
}
