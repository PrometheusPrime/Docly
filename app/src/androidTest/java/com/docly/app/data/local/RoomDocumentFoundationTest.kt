package com.docly.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.docly.app.data.local.db.AppDatabase
import com.docly.app.data.local.db.RoomMigrations
import com.docly.app.data.local.entity.DocumentEntity
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.OcrStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDocumentFoundationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun schemaVersion3FreshResetsToUnifiedDocumentTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DATABASE_NAME)

        migrationHelper.createDatabase(TEST_DATABASE_NAME, 3).use { createdDatabase ->
            createdDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS saved_documents (
                    id TEXT NOT NULL PRIMARY KEY,
                    sessionId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    pdfPath TEXT NOT NULL,
                    thumbnailPath TEXT,
                    grade TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    paperType TEXT NOT NULL,
                    paperNumber TEXT,
                    source TEXT,
                    notes TEXT,
                    pageCount INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }

        val migratedDatabase = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            4,
            true,
            *RoomMigrations.ALL
        )

        listOf(
            "documents",
            "folders",
            "recent_documents",
            "conversion_jobs",
            "scan_sessions",
            "scan_pages"
        ).forEach { tableName ->
            migratedDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'"
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        }
        migratedDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'saved_documents'")
            .use { cursor -> assertTrue(!cursor.moveToFirst()) }
        migratedDatabase.close()
    }

    @Test
    fun documentDaoInsertsSearchesRenamesFavoritesAndDeletes() = runBlocking {
        val dao = database.documentDao()
        val document = documentEntity()

        dao.upsert(document)
        assertEquals(listOf(document), dao.observeAll().first())
        assertEquals(listOf(document), dao.searchByName("%Paper%").first())

        dao.rename(document.id, "Renamed", 2L)
        assertEquals("Renamed", dao.getById(document.id)?.name)

        dao.updateFavorite(document.id, true, 3L)
        assertEquals(true, dao.getById(document.id)?.isFavorite)

        dao.updateLastOpened(document.id, 4L)
        assertEquals(4L, dao.getById(document.id)?.lastOpenedAt)

        dao.delete(checkNotNull(dao.getById(document.id)))
        assertNull(dao.getById(document.id))
    }

    private fun documentEntity(): DocumentEntity = DocumentEntity(
        id = "document-id",
        name = "Paper",
        type = DocumentType.PDF.name,
        mimeType = "application/pdf",
        filePath = "/documents/paper.pdf",
        uri = null,
        source = DocumentSource.IMPORTED.name,
        folderId = null,
        thumbnailPath = null,
        fileSize = 10L,
        pageCount = null,
        createdAt = 1L,
        updatedAt = 1L,
        lastOpenedAt = null,
        isFavorite = false,
        isScanned = false,
        ocrStatus = OcrStatus.NOT_STARTED.name
    )

    private companion object {
        const val TEST_DATABASE_NAME = "room-document-foundation-test"
    }
}
