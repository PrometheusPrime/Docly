package com.docly.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

object RoomMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE scanned_pages ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'ACCEPTED'"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("SELECT 1")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            dropLegacyTables(connection)
            createUnifiedDocumentTables(connection)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

    private fun dropLegacyTables(connection: SQLiteConnection) {
        listOf(
            "document_ocr_fts",
            "document_ocr_results",
            "diagnostic_events",
            "saved_documents",
            "scanned_pages",
            "scan_pages",
            "scan_sessions",
            "recent_documents",
            "conversion_jobs",
            "folders",
            "documents"
        ).forEach { tableName ->
            connection.execSQL("DROP TABLE IF EXISTS $tableName")
        }
    }

    private fun createUnifiedDocumentTables(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS documents (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                mimeType TEXT,
                filePath TEXT,
                uri TEXT,
                source TEXT NOT NULL,
                folderId TEXT,
                thumbnailPath TEXT,
                fileSize INTEGER NOT NULL,
                pageCount INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                lastOpenedAt INTEGER,
                isFavorite INTEGER NOT NULL,
                isScanned INTEGER NOT NULL,
                ocrStatus TEXT NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_documents_folderId ON documents(folderId)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_documents_type ON documents(type)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_documents_updatedAt ON documents(updatedAt)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_documents_lastOpenedAt ON documents(lastOpenedAt)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                parentId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_folders_parentId ON folders(parentId)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recent_documents (
                documentId TEXT NOT NULL PRIMARY KEY,
                openedAt INTEGER NOT NULL,
                FOREIGN KEY(documentId) REFERENCES documents(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_recent_documents_documentId ON recent_documents(documentId)"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_recent_documents_openedAt ON recent_documents(openedAt)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS conversion_jobs (
                id TEXT NOT NULL PRIMARY KEY,
                inputDocumentId TEXT,
                inputUri TEXT,
                inputType TEXT NOT NULL,
                outputType TEXT NOT NULL,
                outputPath TEXT,
                outputDocumentId TEXT,
                status TEXT NOT NULL,
                progress INTEGER NOT NULL,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversion_jobs_inputDocumentId ON conversion_jobs(inputDocumentId)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversion_jobs_outputDocumentId ON conversion_jobs(outputDocumentId)"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_conversion_jobs_updatedAt ON conversion_jobs(updatedAt)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                status TEXT NOT NULL,
                scanMode TEXT NOT NULL,
                grade TEXT,
                subject TEXT,
                year INTEGER,
                paperType TEXT,
                paperNumber TEXT,
                source TEXT,
                notes TEXT
            )
            """.trimIndent()
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_pages (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                pageIndex INTEGER NOT NULL,
                originalImagePath TEXT NOT NULL,
                processedImagePath TEXT,
                thumbnailPath TEXT,
                rotationDegrees INTEGER NOT NULL,
                scanMode TEXT NOT NULL,
                reviewStatus TEXT NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                topLeftX REAL,
                topLeftY REAL,
                topRightX REAL,
                topRightY REAL,
                bottomRightX REAL,
                bottomRightY REAL,
                bottomLeftX REAL,
                bottomLeftY REAL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES scan_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_scan_pages_sessionId ON scan_pages(sessionId)")

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS diagnostic_events (
                id TEXT NOT NULL PRIMARY KEY,
                timestampMillis INTEGER NOT NULL,
                stage TEXT NOT NULL,
                severity TEXT NOT NULL,
                message TEXT NOT NULL,
                relatedDocumentId TEXT,
                relatedSessionId TEXT,
                relatedPageId TEXT,
                throwableClass TEXT
            )
            """.trimIndent()
        )
    }
}
