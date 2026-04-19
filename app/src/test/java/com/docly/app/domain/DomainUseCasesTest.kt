package com.docly.app.domain

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.export.SaveDocumentUseCase
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.library.GetSavedDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveSavedDocumentsUseCase
import com.docly.app.domain.usecase.page.AddProcessedPageUseCase
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
import com.docly.app.domain.usecase.page.UpdatePageUseCase
import com.docly.app.domain.usecase.session.CreateScanSessionUseCase
import com.docly.app.domain.usecase.session.GetLatestInProgressSessionUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanMetadataUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {
    @Test
    fun rotatePageUseCaseWrapsRotationFrom270To0() = runBlocking {
        val repository = FakeScanRepository()
        val page = samplePage(rotationDegrees = 270)

        val result = RotatePageUseCase(repository)(page)

        assertTrue(result is AppResult.Success)
        assertEquals(0, repository.updatedPage?.rotationDegrees)
        assertEquals(page.id, repository.updatedPage?.id)
    }

    @Test
    fun generatePdfUseCaseRejectsEmptyPages() = runBlocking {
        val pdfRepository = FakePdfRepository()
        val result = GeneratePdfUseCase(pdfRepository, FakeFileRepository())(
            fileName = "document.pdf",
            pages = emptyList()
        )

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
        assertTrue(pdfRepository.pageImagePaths.isEmpty())
    }

    @Test
    fun generatePdfUseCasePrefersProcessedImagePath() = runBlocking {
        val pdfRepository = FakePdfRepository()
        val fileRepository = FakeFileRepository()
        val result = GeneratePdfUseCase(pdfRepository, fileRepository)(
            fileName = "document.pdf",
            pages = listOf(
                samplePage(id = "page-1", originalImagePath = "/raw-1.jpg", processedImagePath = "/processed-1.jpg"),
                samplePage(id = "page-2", originalImagePath = "/raw-2.jpg", processedImagePath = null)
            )
        )

        assertEquals("/pdf/document.pdf", result.getOrNull())
        assertEquals(listOf("/processed-1.jpg", "/raw-2.jpg"), pdfRepository.pageImagePaths)
        assertEquals("/pdf/document.pdf", pdfRepository.outputPdfPath)
        assertEquals(StorageReserveBytes.EXPORT_BYTES, fileRepository.requiredBytes)
    }

    @Test
    fun generatePdfUseCaseStopsWhenStorageCheckFails() = runBlocking {
        val pdfRepository = FakePdfRepository()
        val fileRepository = FakeFileRepository().apply {
            storageAvailabilityResult = AppResult.Error(
                message = "No storage.",
                category = AppErrorCategory.STORAGE
            )
        }
        val result = GeneratePdfUseCase(pdfRepository, fileRepository)(
            fileName = "document.pdf",
            pages = listOf(samplePage())
        )

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertTrue(pdfRepository.pageImagePaths.isEmpty())
        assertEquals(0, fileRepository.createPdfPathCalls)
    }

    @Test
    fun sessionUseCasesDelegateToScanRepository() = runBlocking {
        val repository = FakeScanRepository()
        val metadata = sampleMetadata()

        CreateScanSessionUseCase(repository)(ScanMode.COLOR)
        GetScanSessionUseCase(repository)("session-id")
        GetLatestInProgressSessionUseCase(repository)()
        UpdateScanMetadataUseCase(repository)("session-id", metadata)
        UpdateScanSessionStatusUseCase(repository)("session-id", ScanSessionStatus.READY_FOR_EXPORT)

        assertEquals(ScanMode.COLOR, repository.createdScanMode)
        assertEquals("session-id", repository.loadedSessionId)
        assertEquals(1, repository.latestInProgressCalls)
        assertEquals("session-id" to metadata, repository.updatedMetadata)
        assertEquals("session-id" to ScanSessionStatus.READY_FOR_EXPORT, repository.updatedStatus)
    }

    @Test
    fun pageUseCasesDelegateToScanRepository() = runBlocking {
        val repository = FakeScanRepository()
        val page = samplePage()
        val orderedPageIds = listOf("page-2", "page-1")

        AddProcessedPageUseCase(repository)(page)
        UpdatePageUseCase(repository)(page.copy(rotationDegrees = 90))
        DeletePageUseCase(repository)("page-id")
        ReorderPagesUseCase(repository)("session-id", orderedPageIds)

        assertSame(page, repository.addedPage)
        assertEquals(90, repository.updatedPage?.rotationDegrees)
        assertEquals("page-id", repository.deletedPageId)
        assertEquals("session-id" to orderedPageIds, repository.reorderedPages)
    }

    @Test
    fun documentAndLibraryUseCasesDelegateToDocumentRepository() = runBlocking {
        val repository = FakeDocumentRepository()
        val document = sampleDocument()

        SaveDocumentUseCase(repository)(document)
        GetSavedDocumentUseCase(repository)("document-id")
        DeleteSavedDocumentUseCase(repository)("document-id")
        val observedDocuments = ObserveSavedDocumentsUseCase(repository)()

        assertSame(document, repository.savedDocument)
        assertEquals("document-id", repository.loadedDocumentId)
        assertEquals("document-id", repository.deletedDocumentId)
        assertSame(repository.savedDocuments, observedDocuments)
    }

    private fun samplePage(
        id: String = "page-id",
        originalImagePath: String = "/raw.jpg",
        processedImagePath: String? = "/processed.jpg",
        rotationDegrees: Int = 0
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = originalImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = "/thumb.jpg",
        rotationDegrees = rotationDegrees,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

    private fun sampleMetadata(): DocumentMetadata = DocumentMetadata(
        grade = "Grade 10",
        subject = "Math",
        year = 2026,
        paperType = "Past Paper"
    )

    private fun sampleDocument(): SavedDocument = SavedDocument(
        id = "document-id",
        sessionId = "session-id",
        title = "Grade 10 Math",
        pdfPath = "/pdf/document.pdf",
        thumbnailPath = "/thumb.jpg",
        metadata = sampleMetadata(),
        pageCount = 1,
        createdAt = 2L
    )

    private class FakeScanRepository : ScanRepository {
        var createdScanMode: ScanMode? = null
        var loadedSessionId: String? = null
        var latestInProgressCalls = 0
        var updatedMetadata: Pair<String, DocumentMetadata>? = null
        var addedPage: ScannedPage? = null
        var updatedPage: ScannedPage? = null
        var deletedPageId: String? = null
        var reorderedPages: Pair<String, List<String>>? = null
        var updatedStatus: Pair<String, ScanSessionStatus>? = null

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> {
            createdScanMode = scanMode
            return AppResult.Success(sampleSession(scanMode = scanMode))
        }

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> {
            loadedSessionId = sessionId
            return AppResult.Success(sampleSession(id = sessionId))
        }

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> {
            latestInProgressCalls += 1
            return AppResult.Success(sampleSession())
        }

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> {
            updatedMetadata = sessionId to metadata
            return AppResult.Success(Unit)
        }

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> {
            addedPage = page
            return AppResult.Success(Unit)
        }

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> {
            updatedPage = page
            return AppResult.Success(Unit)
        }

        override suspend fun deletePage(pageId: String): AppResult<Unit> {
            deletedPageId = pageId
            return AppResult.Success(Unit)
        }

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> {
            reorderedPages = sessionId to orderedPageIds
            return AppResult.Success(Unit)
        }

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> {
            updatedStatus = sessionId to status
            return AppResult.Success(Unit)
        }

        private fun sampleSession(id: String = "session-id", scanMode: ScanMode = ScanMode.DOCUMENT): ScanSession =
            ScanSession(
                id = id,
                createdAt = 1L,
                updatedAt = 1L,
                status = ScanSessionStatus.IN_PROGRESS,
                scanMode = scanMode
            )
    }

    private class FakeDocumentRepository : DocumentRepository {
        val savedDocuments: Flow<List<SavedDocument>> = flowOf(emptyList())
        var savedDocument: SavedDocument? = null
        var loadedDocumentId: String? = null
        var deletedDocumentId: String? = null

        override fun observeSavedDocuments(): Flow<List<SavedDocument>> = savedDocuments

        override suspend fun saveDocument(document: SavedDocument): AppResult<Unit> {
            savedDocument = document
            return AppResult.Success(Unit)
        }

        override suspend fun getDocument(documentId: String): AppResult<SavedDocument?> {
            loadedDocumentId = documentId
            return AppResult.Success(null)
        }

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
            deletedDocumentId = documentId
            return AppResult.Success(Unit)
        }
    }

    private class FakePdfRepository : PdfRepository {
        var pageImagePaths: List<String> = emptyList()
        var outputPdfPath: String? = null

        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
            this.pageImagePaths = pageImagePaths
            this.outputPdfPath = outputPdfPath
            return AppResult.Success(outputPdfPath)
        }
    }

    private class FakeFileRepository : FileRepository {
        var createPdfPathCalls = 0
        var requiredBytes: Long? = null
        var storageAvailabilityResult: AppResult<Unit> = AppResult.Success(Unit)

        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String {
            createPdfPathCalls += 1
            return "/pdf/$fileName"
        }

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> {
            this.requiredBytes = requiredBytes
            return storageAvailabilityResult
        }

        override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }
}
