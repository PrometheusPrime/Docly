package com.docly.app.domain

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.export.ExportDocumentUseCase
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.export.PrepareExportUseCase
import com.docly.app.domain.usecase.export.SaveDocumentUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportDocumentUseCaseTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun blankSessionIdFailsBeforeLoadingSession() = runBlocking {
        val scanRepository = FakeScanRepository()

        val result = useCase(scanRepository = scanRepository)("")

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
        assertEquals("Scan session not found.", result.errorOrNull()?.message)
        assertTrue(scanRepository.loadedSessionIds.isEmpty())
    }

    @Test
    fun missingSessionFailsReadiness() = runBlocking {
        val result = useCase(scanRepository = FakeScanRepository(session = null))("missing-session")

        assertEquals("Scan session not found.", result.errorOrNull()?.message)
    }

    @Test
    fun missingMetadataFailsReadiness() = runBlocking {
        val result = useCase(
            scanRepository = FakeScanRepository(session = sampleSession(metadata = null))
        )(SESSION_ID)

        assertEquals("Document details are required before export.", result.errorOrNull()?.message)
    }

    @Test
    fun invalidMetadataFailsReadiness() = runBlocking {
        val result = useCase(
            scanRepository = FakeScanRepository(
                session = sampleSession(metadata = sampleMetadata(year = 1979))
            )
        )(SESSION_ID)

        assertEquals("Year must be between 1980 and 2027.", result.errorOrNull()?.message)
    }

    @Test
    fun pendingPagesFailReadiness() = runBlocking {
        val result = useCase(
            scanRepository = FakeScanRepository(
                session = sampleSession(
                    pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
                )
            )
        )(SESSION_ID)

        assertEquals("Review all pages before export.", result.errorOrNull()?.message)
    }

    @Test
    fun noAcceptedPagesFailReadiness() = runBlocking {
        val result = useCase(
            scanRepository = FakeScanRepository(session = sampleSession(pages = emptyList()))
        )(SESSION_ID)

        assertEquals("At least one reviewed page is required before export.", result.errorOrNull()?.message)
    }

    @Test
    fun missingProcessedPathFailsReadiness() = runBlocking {
        val result = useCase(
            scanRepository = FakeScanRepository(
                session = sampleSession(pages = listOf(samplePage(processedImagePath = null)))
            )
        )(SESSION_ID)

        assertEquals("Process every page before export.", result.errorOrNull()?.message)
    }

    @Test
    fun missingProcessedFileFailsReadiness() = runBlocking {
        val missingPath = File(temporaryFolder.root, "missing-page.jpg").absolutePath
        val result = useCase(
            scanRepository = FakeScanRepository(
                session = sampleSession(pages = listOf(samplePage(processedImagePath = missingPath)))
            )
        )(SESSION_ID)

        assertEquals(
            "Processed page image is missing. Reprocess the page before export.",
            result.errorOrNull()?.message
        )
    }

    @Test
    fun pdfGenerationFailureStopsBeforeSavingDocument() = runBlocking {
        val documentRepository = FakeDocumentRepository()
        val scanRepository = FakeScanRepository(session = sampleSessionWithExistingPage())
        val result = useCase(
            scanRepository = scanRepository,
            documentRepository = documentRepository,
            pdfRepository = FakePdfRepository(
                createResult = AppResult.Error("PDF failed.", AppErrorCategory.PDF)
            )
        )(SESSION_ID)

        assertEquals(AppErrorCategory.PDF, result.errorOrNull()?.category)
        assertNull(documentRepository.savedDocument)
        assertNull(scanRepository.updatedStatus)
    }

    @Test
    fun saveDocumentFailureDeletesGeneratedPdf() = runBlocking {
        val fileRepository = FakeFileRepository(temporaryFolder.root)
        val documentRepository = FakeDocumentRepository(
            saveResult = AppResult.Error("Save failed.", AppErrorCategory.STORAGE)
        )
        val result = useCase(
            scanRepository = FakeScanRepository(session = sampleSessionWithExistingPage()),
            documentRepository = documentRepository,
            fileRepository = fileRepository
        )(SESSION_ID)

        val deletedPdfPath = fileRepository.deletedFiles.single()
        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertFalse(File(deletedPdfPath).exists())
    }

    @Test
    fun statusUpdateFailureDeletesSavedDocumentAndGeneratedPdf() = runBlocking {
        val fileRepository = FakeFileRepository(temporaryFolder.root)
        val documentRepository = FakeDocumentRepository()
        val result = useCase(
            scanRepository = FakeScanRepository(
                session = sampleSessionWithExistingPage(),
                statusResult = AppResult.Error("Status failed.", AppErrorCategory.STORAGE)
            ),
            documentRepository = documentRepository,
            fileRepository = fileRepository
        )(SESSION_ID)

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(listOf(DOCUMENT_ID), documentRepository.deletedDocumentIds)
        assertEquals(1, fileRepository.deletedFiles.size)
        assertFalse(File(fileRepository.deletedFiles.single()).exists())
    }

    @Test
    fun successfulExportSavesDocumentAndMarksSessionExported() = runBlocking {
        val fileRepository = FakeFileRepository(temporaryFolder.root)
        val pdfRepository = FakePdfRepository()
        val documentRepository = FakeDocumentRepository()
        val scanRepository = FakeScanRepository(
            session = sampleSessionWithExistingPage(
                pages = listOf(
                    samplePage(id = "second-page", pageIndex = 1, processedImagePath = existingImage("second.jpg")),
                    samplePage(id = "first-page", pageIndex = 0, processedImagePath = existingImage("first.jpg"))
                )
            )
        )

        val result = useCase(
            scanRepository = scanRepository,
            pdfRepository = pdfRepository,
            documentRepository = documentRepository,
            fileRepository = fileRepository
        )(SESSION_ID)

        val document = result.getOrNull()?.document
        assertEquals(DOCUMENT_ID, document?.id)
        assertEquals(SESSION_ID, document?.sessionId)
        assertEquals("grade_10_math_2026_past_paper_1", document?.title)
        assertEquals(2, document?.pageCount)
        assertEquals("/thumb/first-page.jpg", document?.thumbnailPath)
        assertEquals(document, documentRepository.savedDocument)
        assertEquals(SESSION_ID to ScanSessionStatus.EXPORTED, scanRepository.updatedStatus)
        assertEquals(
            listOf(existingImagePath("first.jpg"), existingImagePath("second.jpg")),
            pdfRepository.pageImagePaths
        )
    }

    private fun useCase(
        scanRepository: FakeScanRepository = FakeScanRepository(session = sampleSessionWithExistingPage()),
        pdfRepository: FakePdfRepository = FakePdfRepository(),
        documentRepository: FakeDocumentRepository = FakeDocumentRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(temporaryFolder.root)
    ): ExportDocumentUseCase {
        val prepareExportUseCase = PrepareExportUseCase(
            getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
            generateDocumentNameUseCase = GenerateDocumentNameUseCase(),
            validateMetadataUseCase = ValidateMetadataUseCase(FixedTimeProvider(FIXED_NOW))
        )
        return ExportDocumentUseCase(
            prepareExportUseCase = prepareExportUseCase,
            generatePdfUseCase = GeneratePdfUseCase(pdfRepository, fileRepository),
            saveDocumentUseCase = SaveDocumentUseCase(documentRepository),
            updateScanSessionStatusUseCase = UpdateScanSessionStatusUseCase(scanRepository),
            deleteSavedDocumentUseCase = DeleteSavedDocumentUseCase(documentRepository),
            fileRepository = fileRepository,
            idProvider = FixedIdProvider(DOCUMENT_ID),
            timeProvider = FixedTimeProvider(FIXED_NOW)
        )
    }

    private fun sampleSessionWithExistingPage(
        pages: List<ScannedPage> = listOf(samplePage(processedImagePath = existingImage("page.jpg")))
    ): ScanSession = sampleSession(pages = pages)

    private fun sampleSession(
        metadata: DocumentMetadata? = sampleMetadata(),
        pages: List<ScannedPage> = listOf(samplePage(processedImagePath = existingImage("page.jpg")))
    ): ScanSession = ScanSession(
        id = SESSION_ID,
        createdAt = 1L,
        updatedAt = 1L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = ScanMode.DOCUMENT,
        pages = pages,
        metadata = metadata
    )

    private fun samplePage(
        id: String = "page-id",
        pageIndex: Int = 0,
        processedImagePath: String? = existingImage("page-$id.jpg"),
        reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = SESSION_ID,
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = processedImagePath,
        thumbnailPath = "/thumb/$id.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private fun sampleMetadata(year: Int = 2026): DocumentMetadata = DocumentMetadata(
        grade = "Grade 10",
        subject = "Math",
        year = year,
        paperType = "Past Paper",
        paperNumber = "1"
    )

    private fun existingImage(name: String): String {
        val file = File(temporaryFolder.root, "processed/$name")
        file.parentFile?.mkdirs()
        file.writeText("image")
        return file.absolutePath
    }

    private fun existingImagePath(name: String): String = File(temporaryFolder.root, "processed/$name").absolutePath

    private class FakeScanRepository(
        private val session: ScanSession? = null,
        private val statusResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        val loadedSessionIds = mutableListOf<String>()
        var updatedStatus: Pair<String, ScanSessionStatus>? = null

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> {
            loadedSessionIds += sessionId
            return AppResult.Success(session)
        }

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun deletePage(pageId: String): AppResult<Unit> = error("Not used.")

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            error("Not used.")

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            when (statusResult) {
                is AppResult.Error -> statusResult

                is AppResult.Success -> {
                    updatedStatus = sessionId to status
                    AppResult.Success(Unit)
                }
            }
    }

    private class FakePdfRepository(private val createResult: AppResult<String>? = null) : PdfRepository {
        var pageImagePaths: List<String> = emptyList()
        var outputPdfPath: String = ""

        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
            this.pageImagePaths = pageImagePaths
            this.outputPdfPath = outputPdfPath
            val configuredResult = createResult
            if (configuredResult != null) return configuredResult

            File(outputPdfPath).apply {
                parentFile?.mkdirs()
                writeText("pdf")
            }
            return AppResult.Success(outputPdfPath)
        }
    }

    private class FakeDocumentRepository(private val saveResult: AppResult<Unit> = AppResult.Success(Unit)) :
        DocumentRepository {
        var savedDocument: SavedDocument? = null
        val deletedDocumentIds = mutableListOf<String>()

        override fun observeSavedDocuments(): Flow<List<SavedDocument>> = flowOf(emptyList())

        override suspend fun saveDocument(document: SavedDocument): AppResult<Unit> = when (saveResult) {
            is AppResult.Error -> saveResult

            is AppResult.Success -> {
                savedDocument = document
                AppResult.Success(Unit)
            }
        }

        override suspend fun getDocument(documentId: String): AppResult<SavedDocument?> = AppResult.Success(null)

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
            deletedDocumentIds += documentId
            savedDocument = null
            return AppResult.Success(Unit)
        }
    }

    private class FakeFileRepository(private val root: File) : FileRepository {
        val deletedFiles = mutableListOf<String>()

        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = File(root, "pdf/$fileName").absolutePath

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> {
            deletedFiles += path
            File(path).delete()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FixedIdProvider(private val id: String) : IdProvider {
        override fun generateId(): String = id
    }

    private companion object {
        const val SESSION_ID = "session-id"
        const val DOCUMENT_ID = "document-id"
        val FIXED_NOW: Long = GregorianCalendar(2026, Calendar.JANUARY, 1).timeInMillis
    }
}
