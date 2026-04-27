package com.docly.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.local.db.AppDatabase
import com.docly.app.data.local.db.RoomMigrations
import com.docly.app.data.local.entity.SavedDocumentEntity
import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.data.local.entity.ScannedPageEntity
import com.docly.app.data.repository.DocumentRepositoryImpl
import com.docly.app.data.repository.ScanRepositoryImpl
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
class RoomRepositoryTest {
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
    fun schemaVersion1MigratesToVersion2WithAcceptedReviewStatusDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DATABASE_NAME)

        migrationHelper.createDatabase(TEST_DATABASE_NAME, 1).use { createdDatabase ->
            createdDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scan_sessions'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                }
            createdDatabase.execSQL(
                """
                INSERT INTO scan_sessions (
                    id, createdAt, updatedAt, status, scanMode,
                    grade, subject, year, paperType, paperNumber, source, notes
                ) VALUES (
                    'session-id', 1, 1, 'IN_PROGRESS', 'DOCUMENT',
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL
                )
                """.trimIndent()
            )
            createdDatabase.execSQL(
                """
                INSERT INTO scanned_pages (
                    id, sessionId, pageIndex, originalImagePath, processedImagePath, thumbnailPath,
                    rotationDegrees, scanMode, width, height,
                    topLeftX, topLeftY, topRightX, topRightY,
                    bottomRightX, bottomRightY, bottomLeftX, bottomLeftY, createdAt
                ) VALUES (
                    'page-id', 'session-id', 0, '/raw/page.jpg', '/processed/page.jpg', '/thumb/page.jpg',
                    0, 'DOCUMENT', 100, 200,
                    NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL, 1
                )
                """.trimIndent()
            )
        }

        val migratedDatabase = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            2,
            true,
            *RoomMigrations.ALL
        )
        migratedDatabase.query("SELECT reviewStatus FROM scanned_pages WHERE id = 'page-id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(PageReviewStatus.ACCEPTED.name, cursor.getString(0))
        }
        migratedDatabase.close()

        val roomDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DATABASE_NAME
        ).addMigrations(*RoomMigrations.ALL)
            .build()

        roomDatabase.openHelper.writableDatabase.close()
        roomDatabase.close()
    }

    @Test
    fun scanSessionDaoInsertsReadsUpdatesAndDeletes() = runBlocking {
        val dao = database.scanSessionDao()
        val session = scanSessionEntity()

        dao.insert(session)
        assertEquals(session, dao.getById(session.id))

        val updated = session.copy(
            updatedAt = 20L,
            status = ScanSessionStatus.READY_FOR_EXPORT.name,
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper"
        )
        dao.update(updated)
        assertEquals(updated, dao.getById(session.id))

        dao.delete(updated)
        assertNull(dao.getById(session.id))
    }

    @Test
    fun scannedPageDaoOrdersUpdatesDeletesAndCascadesPages() = runBlocking {
        val sessionDao = database.scanSessionDao()
        val pageDao = database.scannedPageDao()
        val session = scanSessionEntity()
        val firstPage = scannedPageEntity(id = "first-page", pageIndex = 0)
        val secondPage = scannedPageEntity(id = "second-page", pageIndex = 1)

        sessionDao.insert(session)
        pageDao.insert(secondPage)
        pageDao.insert(firstPage)

        assertEquals(listOf("first-page", "second-page"), pageDao.getBySessionId(session.id).map { it.id })

        val updatedFirstPage = firstPage.copy(rotationDegrees = 90, processedImagePath = "/processed/first-page.jpg")
        pageDao.update(updatedFirstPage)
        assertEquals(updatedFirstPage, pageDao.getById(firstPage.id))

        pageDao.delete(updatedFirstPage)
        assertNull(pageDao.getById(firstPage.id))

        pageDao.deleteBySessionId(session.id)
        assertTrue(pageDao.getBySessionId(session.id).isEmpty())

        val cascadeSession = scanSessionEntity(id = "cascade-session")
        val cascadePage = scannedPageEntity(id = "cascade-page", sessionId = cascadeSession.id)
        sessionDao.insert(cascadeSession)
        pageDao.insert(cascadePage)
        sessionDao.delete(cascadeSession)

        assertTrue(pageDao.getBySessionId(cascadeSession.id).isEmpty())
    }

    @Test
    fun savedDocumentDaoInsertsObservesReadsUpdatesAndDeletes() = runBlocking {
        val dao = database.savedDocumentDao()
        val olderDocument = savedDocumentEntity(id = "older-document", createdAt = 10L)
        val newerDocument = savedDocumentEntity(id = "newer-document", createdAt = 20L)

        dao.insert(olderDocument)
        dao.insert(newerDocument)

        assertEquals(listOf("newer-document", "older-document"), dao.observeAll().first().map { it.id })
        assertEquals(olderDocument, dao.getById(olderDocument.id))

        val updatedOlderDocument = olderDocument.copy(title = "Updated title", pageCount = 3)
        dao.update(updatedOlderDocument)
        assertEquals(updatedOlderDocument, dao.getById(olderDocument.id))

        dao.delete(updatedOlderDocument)
        assertNull(dao.getById(olderDocument.id))
    }

    @Test
    fun scanRepositoryPersistsUpdatesAndDeletesSessionData() = runBlocking {
        val repository = scanRepository()
        val metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1",
            source = "School",
            notes = "Term 1"
        )
        val session = repository.createSession(ScanMode.DOCUMENT).successData()

        repository.updateMetadata(session.id, metadata).assertSuccess()
        repository.updateSessionStatus(session.id, ScanSessionStatus.READY_FOR_EXPORT).assertSuccess()
        repository.addPage(page(id = "page-id", sessionId = session.id, pageIndex = 0)).assertSuccess()

        val loadedAfterInsert = repository.getSession(session.id).successData()
        assertEquals(metadata, loadedAfterInsert?.metadata)
        assertEquals(ScanSessionStatus.READY_FOR_EXPORT, loadedAfterInsert?.status)
        assertEquals(listOf("page-id"), loadedAfterInsert?.pages?.map { it.id })

        val updatedPage = loadedAfterInsert?.pages?.first()?.copy(
            rotationDegrees = 180,
            processedImagePath = "/processed/page-id.jpg"
        )
        checkNotNull(updatedPage)
        repository.updatePage(updatedPage).assertSuccess()

        val loadedAfterPageUpdate = repository.getSession(session.id).successData()
        assertEquals(180, loadedAfterPageUpdate?.pages?.single()?.rotationDegrees)
        assertEquals("/processed/page-id.jpg", loadedAfterPageUpdate?.pages?.single()?.processedImagePath)
        assertEquals(PageReviewStatus.ACCEPTED, loadedAfterPageUpdate?.pages?.single()?.reviewStatus)

        repository.deletePage("page-id").assertSuccess()
        assertTrue(repository.getSession(session.id).successData()?.pages?.isEmpty() == true)
    }

    @Test
    fun scanRepositoryDeletePageDeletesDatabaseRowThenCleansPageAssets() = runBlocking {
        val fileRepository = FakeFileRepository()
        val repository = scanRepository(fileRepository = fileRepository)
        val session = repository.createSession(ScanMode.DOCUMENT).successData()
        val page = page(
            id = "page-id",
            sessionId = session.id,
            pageIndex = 0,
            processedImagePath = "/processed/page-id.jpg",
            thumbnailPath = "/thumbnails/page-id.jpg"
        )
        repository.addPage(page).assertSuccess()

        repository.deletePage(page.id).assertSuccess()

        assertTrue(repository.getSession(session.id).successData()?.pages?.isEmpty() == true)
        assertEquals(listOf(page), fileRepository.deletedPageAssets)
    }

    @Test
    fun scanRepositoryDeletePageKeepsDatabaseDeletionWhenCleanupFails() = runBlocking {
        val fileRepository = FakeFileRepository(
            pageDeleteResult = AppResult.Error("Cleanup failed.", AppErrorCategory.STORAGE)
        )
        val repository = scanRepository(fileRepository = fileRepository)
        val session = repository.createSession(ScanMode.DOCUMENT).successData()
        val page = page(id = "page-id", sessionId = session.id, pageIndex = 0)
        repository.addPage(page).assertSuccess()

        val result = repository.deletePage(page.id)

        assertEquals(AppErrorCategory.STORAGE, result.errorCategory())
        assertTrue(repository.getSession(session.id).successData()?.pages?.isEmpty() == true)
        assertEquals(listOf(page), fileRepository.deletedPageAssets)
    }

    @Test
    fun scanRepositoryPersistsPagesInPageIndexOrderAndReordersThem() = runBlocking {
        val repository = scanRepository()
        val session = repository.createSession(ScanMode.DOCUMENT).successData()

        repository.addPage(page(id = "second-page", sessionId = session.id, pageIndex = 1)).assertSuccess()
        repository.addPage(page(id = "first-page", sessionId = session.id, pageIndex = 0)).assertSuccess()

        val loadedBeforeReorder = repository.getSession(session.id).successData()
        assertEquals(listOf("first-page", "second-page"), loadedBeforeReorder?.pages?.map { it.id })

        repository.reorderPages(session.id, listOf("second-page", "first-page")).assertSuccess()

        val loadedAfterReorder = repository.getSession(session.id).successData()
        assertEquals(listOf("second-page", "first-page"), loadedAfterReorder?.pages?.map { it.id })

        val duplicateResult = repository.reorderPages(session.id, listOf("second-page", "second-page"))
        val missingResult = repository.reorderPages(session.id, listOf("second-page"))

        assertEquals(AppErrorCategory.VALIDATION, duplicateResult.errorCategory())
        assertEquals(AppErrorCategory.VALIDATION, missingResult.errorCategory())
    }

    @Test
    fun documentRepositorySavesObservesReadsUpdatesAndDeletesDocuments() = runBlocking {
        val repository = DocumentRepositoryImpl(
            savedDocumentDao = database.savedDocumentDao(),
            dispatcherProvider = UnconfinedDispatcherProvider(),
            fileRepository = FakeFileRepository()
        )
        val document = savedDocument()

        repository.saveDocument(document).assertSuccess()
        assertEquals(listOf(document), repository.observeSavedDocuments().first())
        assertEquals(document, repository.getDocument(document.id).successData())

        val updatedDocument = document.copy(title = "Updated title", pageCount = 3)
        repository.saveDocument(updatedDocument).assertSuccess()
        assertEquals(updatedDocument, repository.getDocument(document.id).successData())

        repository.deleteDocument(document.id).assertSuccess()
        assertNull(repository.getDocument(document.id).successData())
        assertTrue(repository.observeSavedDocuments().first().isEmpty())
    }

    @Test
    fun documentRepositoryDeleteDocumentDeletesDatabaseRowThenCleansDocumentAssets() = runBlocking {
        val fileRepository = FakeFileRepository()
        val repository = documentRepository(fileRepository = fileRepository)
        val document = savedDocument()
        repository.saveDocument(document).assertSuccess()

        repository.deleteDocument(document.id).assertSuccess()

        assertNull(repository.getDocument(document.id).successData())
        assertEquals(listOf(document), fileRepository.deletedDocumentAssets)
    }

    @Test
    fun documentRepositoryDeleteDocumentKeepsDatabaseDeletionWhenCleanupFails() = runBlocking {
        val fileRepository = FakeFileRepository(
            documentDeleteResult = AppResult.Error("Cleanup failed.", AppErrorCategory.STORAGE)
        )
        val repository = documentRepository(fileRepository = fileRepository)
        val document = savedDocument()
        repository.saveDocument(document).assertSuccess()

        val result = repository.deleteDocument(document.id)

        assertEquals(AppErrorCategory.STORAGE, result.errorCategory())
        assertNull(repository.getDocument(document.id).successData())
        assertEquals(listOf(document), fileRepository.deletedDocumentAssets)
    }

    private fun scanRepository(fileRepository: FileRepository = FakeFileRepository()): ScanRepositoryImpl =
        ScanRepositoryImpl(
            database = database,
            scanSessionDao = database.scanSessionDao(),
            scannedPageDao = database.scannedPageDao(),
            idProvider = IncrementingIdProvider(),
            timeProvider = FixedTimeProvider(),
            dispatcherProvider = UnconfinedDispatcherProvider(),
            fileRepository = fileRepository
        )

    private fun documentRepository(fileRepository: FileRepository = FakeFileRepository()): DocumentRepositoryImpl =
        DocumentRepositoryImpl(
            savedDocumentDao = database.savedDocumentDao(),
            dispatcherProvider = UnconfinedDispatcherProvider(),
            fileRepository = fileRepository
        )

    private fun scanSessionEntity(
        id: String = "session-id",
        createdAt: Long = 10L,
        updatedAt: Long = 10L,
        status: String = ScanSessionStatus.IN_PROGRESS.name,
        scanMode: String = ScanMode.DOCUMENT.name
    ): ScanSessionEntity = ScanSessionEntity(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        scanMode = scanMode,
        grade = null,
        subject = null,
        year = null,
        paperType = null,
        paperNumber = null,
        source = null,
        notes = null
    )

    private fun scannedPageEntity(
        id: String = "page-id",
        sessionId: String = "session-id",
        pageIndex: Int = 0
    ): ScannedPageEntity = ScannedPageEntity(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT.name,
        reviewStatus = PageReviewStatus.ACCEPTED.name,
        width = 100,
        height = 200,
        topLeftX = null,
        topLeftY = null,
        topRightX = null,
        topRightY = null,
        bottomRightX = null,
        bottomRightY = null,
        bottomLeftX = null,
        bottomLeftY = null,
        createdAt = 10L
    )

    private fun page(
        id: String,
        sessionId: String,
        pageIndex: Int,
        processedImagePath: String? = null,
        thumbnailPath: String? = null
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        originalImagePath = "/$id/raw.jpg",
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 10L
    )

    private fun savedDocumentEntity(id: String = "document-id", createdAt: Long = 10L): SavedDocumentEntity =
        SavedDocumentEntity(
            id = id,
            sessionId = "session-id",
            title = "Title",
            pdfPath = "/documents/$id.pdf",
            thumbnailPath = "/thumbnails/$id.jpg",
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1",
            source = "School",
            notes = "Notes",
            pageCount = 2,
            createdAt = createdAt
        )

    private fun savedDocument(): SavedDocument = SavedDocument(
        id = "document-id",
        sessionId = "session-id",
        title = "Title",
        pdfPath = "/documents/document-id.pdf",
        thumbnailPath = "/thumbnails/document-id.jpg",
        metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1",
            source = "School",
            notes = "Notes"
        ),
        pageCount = 2,
        createdAt = 10L
    )

    private fun AppResult<Unit>.assertSuccess() {
        assertTrue(this is AppResult.Success)
    }

    private fun <T> AppResult<T>.successData(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> throw AssertionError("Expected success, got $this")
    }

    private fun AppResult<*>.errorCategory(): AppErrorCategory? = when (this) {
        is AppResult.Success -> null
        is AppResult.Error -> category
    }

    private class IncrementingIdProvider : IdProvider {
        private var nextId = 0

        override fun generateId(): String {
            nextId += 1
            return "session-$nextId"
        }
    }

    private class FixedTimeProvider : TimeProvider {
        override fun now(): Long = 10L
    }

    private class UnconfinedDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeFileRepository(
        private val pageDeleteResult: AppResult<Unit> = AppResult.Success(Unit),
        private val documentDeleteResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : FileRepository {
        val deletedPageAssets = mutableListOf<ScannedPage>()
        val deletedDocumentAssets = mutableListOf<SavedDocument>()

        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> {
            deletedPageAssets += page
            return pageDeleteResult
        }

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> {
            deletedDocumentAssets += document
            return documentDeleteResult
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "docly-migration-test.db"
    }
}
