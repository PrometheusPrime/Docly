package com.docly.app.domain

import com.docly.app.core.common.IdProvider
import com.docly.app.core.image.ScanPageRenderer
import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.ConversionJob
import com.docly.app.domain.model.DiagnosticEvent
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.OrphanCleanupResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.CleanupRepository
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.DiagnosticsRepository
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.scanner.ImportScannedPagesUseCase
import com.docly.app.domain.usecase.scanner.SaveScannedOutputUseCase
import com.docly.app.domain.usecase.scanner.ScannedOutputFormat
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Phase3ScannerUseCasesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun importScannedPagesCreatesAcceptedPagesInScannerOrder() = runBlocking {
        val scanRepository = FakeScanRepository()
        val devicePhotoRepository = FakeDevicePhotoRepository().apply {
            importedImagesByUri["content://first"] = ImportedRawImage("/raw/first.jpg", width = 100, height = 200)
            importedImagesByUri["content://second"] = ImportedRawImage("/raw/second.jpg", width = 300, height = 400)
        }

        val result = importUseCase(
            scanRepository = scanRepository,
            devicePhotoRepository = devicePhotoRepository,
            idProvider = SequenceIdProvider(listOf("page-1", "page-2"))
        )(
            sessionId = null,
            pageImageUris = listOf("content://first", "content://second"),
            scanMode = ScanMode.DOCUMENT
        )

        val pages = result.getOrNull()?.importedPages.orEmpty()
        assertEquals("session-1", result.getOrNull()?.sessionId)
        assertEquals(listOf("content://first", "content://second"), devicePhotoRepository.requests)
        assertEquals(listOf("page-1", "page-2"), pages.map { page -> page.id })
        assertEquals(listOf(0, 1), pages.map { page -> page.pageIndex })
        assertEquals(
            listOf(PageReviewStatus.ACCEPTED, PageReviewStatus.ACCEPTED),
            pages.map { page ->
                page.reviewStatus
            }
        )
        assertEquals(listOf("/raw/first.jpg", "/raw/second.jpg"), pages.map { page -> page.processedImagePath })
    }

    @Test
    fun importScannedPagesReturnsValidationForEmptyResult() = runBlocking {
        val scanRepository = FakeScanRepository()

        val result = importUseCase(scanRepository = scanRepository)(
            sessionId = null,
            pageImageUris = listOf(" "),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
        assertEquals(0, scanRepository.createSessionCalls)
    }

    @Test
    fun savePdfRendersRotatedPageAndRegistersScannedPdf() = runBlocking {
        val sourceFile = temporaryFolder.writeFile("raw/page.jpg", "raw-image")
        val scanRepository = FakeScanRepository(
            initialSession = sampleSession(
                pages = listOf(
                    samplePage(
                        id = "page-1",
                        originalImagePath = sourceFile.absolutePath,
                        processedImagePath = sourceFile.absolutePath,
                        rotationDegrees = 90
                    )
                )
            )
        )
        val documentRepository = FakeDocumentRepository()
        val pdfRepository = FakePdfRepository(temporaryFolder)
        val renderer = FakeScanPageRenderer()

        val result = saveUseCase(
            scanRepository = scanRepository,
            documentRepository = documentRepository,
            pdfRepository = pdfRepository,
            renderer = renderer
        )(
            sessionId = "session-1",
            title = "Receipt",
            outputFormat = ScannedOutputFormat.PDF
        )

        val document = result.getOrNull()?.documents?.single()
        assertEquals(listOf("page-1"), renderer.renderedPageIds)
        assertEquals(listOf(renderer.renderedPaths.single()), pdfRepository.pageImagePaths)
        assertEquals("Receipt", document?.name)
        assertEquals("application/pdf", document?.mimeType)
        assertEquals(1, document?.pageCount)
        assertEquals("session-1", document?.sourceScanSessionId)
        assertEquals(ScanSessionStatus.EXPORTED, scanRepository.statusBySessionId["session-1"])
        assertEquals(listOf(document?.id), documentRepository.documents.keys.toList())
    }

    @Test
    fun saveImagesCopiesPagesAndRegistersImageDocuments() = runBlocking {
        val firstFile = temporaryFolder.writeFile("raw/first.jpg", "first")
        val secondFile = temporaryFolder.writeFile("raw/second.jpg", "second")
        val scanRepository = FakeScanRepository(
            initialSession = sampleSession(
                pages = listOf(
                    samplePage(id = "page-1", pageIndex = 0, originalImagePath = firstFile.absolutePath),
                    samplePage(id = "page-2", pageIndex = 1, originalImagePath = secondFile.absolutePath)
                )
            )
        )
        val documentRepository = FakeDocumentRepository()

        val result = saveUseCase(
            scanRepository = scanRepository,
            documentRepository = documentRepository
        )(
            sessionId = "session-1",
            title = "Notes",
            outputFormat = ScannedOutputFormat.IMAGES
        )

        val documents = result.getOrNull()?.documents.orEmpty()
        assertEquals(2, documents.size)
        assertEquals(listOf("Notes Page 1", "Notes Page 2"), documents.map { document -> document.name })
        assertTrue(documents.all { document -> document.mimeType == "image/jpeg" })
        assertEquals("first", File(documents[0].internalPath()).readText())
        assertEquals("second", File(documents[1].internalPath()).readText())
        assertEquals(ScanSessionStatus.EXPORTED, scanRepository.statusBySessionId["session-1"])
    }

    @Test
    fun saveRequiresTitle() = runBlocking {
        val result = saveUseCase()(
            sessionId = "session-1",
            title = " ",
            outputFormat = ScannedOutputFormat.PDF
        )

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
    }

    private fun importUseCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        devicePhotoRepository: FakeDevicePhotoRepository = FakeDevicePhotoRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(temporaryFolder),
        imageProcessingRepository: FakeImageProcessingRepository = FakeImageProcessingRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1")),
        timeProvider: TimeProvider = FixedTimeProvider(100L)
    ): ImportScannedPagesUseCase = ImportScannedPagesUseCase(
        scanRepository = scanRepository,
        devicePhotoRepository = devicePhotoRepository,
        fileRepository = fileRepository,
        imageProcessingRepository = imageProcessingRepository,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private fun saveUseCase(
        scanRepository: FakeScanRepository = FakeScanRepository(initialSession = sampleSession()),
        documentRepository: FakeDocumentRepository = FakeDocumentRepository(),
        pdfRepository: FakePdfRepository = FakePdfRepository(temporaryFolder),
        fileRepository: FakeFileRepository = FakeFileRepository(temporaryFolder),
        renderer: FakeScanPageRenderer = FakeScanPageRenderer(),
        idProvider: IdProvider = SequenceIdProvider(listOf("document-1", "document-2")),
        timeProvider: TimeProvider = FixedTimeProvider(200L)
    ): SaveScannedOutputUseCase = SaveScannedOutputUseCase(
        scanRepository = scanRepository,
        documentRepository = documentRepository,
        pdfRepository = pdfRepository,
        fileRepository = fileRepository,
        scanPageRenderer = renderer,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private fun sampleSession(id: String = "session-1", pages: List<ScannedPage> = listOf(samplePage())): ScanSession =
        ScanSession(
            id = id,
            createdAt = 0L,
            updatedAt = 0L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = ScanMode.DOCUMENT,
            pages = pages
        )

    private fun samplePage(
        id: String = "page-1",
        sessionId: String = "session-1",
        pageIndex: Int = 0,
        originalImagePath: String = "",
        processedImagePath: String? = originalImagePath,
        rotationDegrees: Int = 0
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        originalImagePath = originalImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = null,
        rotationDegrees = rotationDegrees,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 0L,
        reviewStatus = PageReviewStatus.ACCEPTED
    )

    private fun TemporaryFolder.writeFile(path: String, content: String): File {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun DoclyDocument.internalPath(): String = (fileRef as com.docly.app.domain.model.FileRef.InternalFile).path
}

private class SequenceIdProvider(ids: List<String>) : IdProvider {
    private val iterator = ids.iterator()

    override fun generateId(): String {
        check(iterator.hasNext()) { "No more test IDs are available." }
        return iterator.next()
    }
}

private class FixedTimeProvider(private val timestampMillis: Long) : TimeProvider {
    override fun now(): Long = timestampMillis
}

private class FakeScanRepository(initialSession: ScanSession? = null) : ScanRepository {
    private val sessions = mutableMapOf<String, ScanSession>()
    val statusBySessionId = mutableMapOf<String, ScanSessionStatus>()
    var createSessionCalls = 0

    init {
        if (initialSession != null) {
            sessions[initialSession.id] = initialSession
        }
    }

    override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> {
        createSessionCalls += 1
        val session = ScanSession(
            id = "session-$createSessionCalls",
            createdAt = 0L,
            updatedAt = 0L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = scanMode
        )
        sessions[session.id] = session
        return AppResult.Success(session)
    }

    override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(sessions[sessionId])

    override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> =
        AppResult.Success(sessions.values.firstOrNull { session -> session.status == ScanSessionStatus.IN_PROGRESS })

    override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun addPage(page: ScannedPage): AppResult<Unit> {
        val session = sessions.getValue(page.sessionId)
        sessions[page.sessionId] = session.copy(pages = session.pages + page)
        return AppResult.Success(Unit)
    }

    override suspend fun updatePage(page: ScannedPage): AppResult<Unit> {
        val session = sessions.getValue(page.sessionId)
        sessions[page.sessionId] = session.copy(
            pages = session.pages.map { existingPage -> if (existingPage.id == page.id) page else existingPage }
        )
        return AppResult.Success(Unit)
    }

    override suspend fun deletePage(pageId: String): AppResult<Unit> {
        sessions.replaceAll { _, session ->
            session.copy(pages = session.pages.filterNot { page -> page.id == pageId })
        }
        return AppResult.Success(Unit)
    }

    override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> {
        val session = sessions.getValue(sessionId)
        val pagesById = session.pages.associateBy { page -> page.id }
        sessions[sessionId] = session.copy(
            pages = orderedPageIds.mapIndexed { index, pageId -> pagesById.getValue(pageId).copy(pageIndex = index) }
        )
        return AppResult.Success(Unit)
    }

    override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> {
        statusBySessionId[sessionId] = status
        sessions[sessionId] = sessions.getValue(sessionId).copy(status = status)
        return AppResult.Success(Unit)
    }
}

private class FakeDevicePhotoRepository : DevicePhotoRepository {
    val requests = mutableListOf<String>()
    val importedImagesByUri = mutableMapOf<String, ImportedRawImage>()

    override suspend fun importRawPhoto(
        sessionId: String,
        pageId: String,
        sourceUri: String
    ): AppResult<ImportedRawImage> {
        requests += sourceUri
        return AppResult.Success(
            importedImagesByUri[sourceUri] ?: ImportedRawImage(
                path = "/raw/$pageId.jpg",
                width = 100,
                height = 200
            )
        )
    }
}

private class FakeFileRepository(private val temporaryFolder: TemporaryFolder) : FileRepository {
    override fun createSessionImagePath(sessionId: String, suffix: String): String =
        File(temporaryFolder.root, "raw/$suffix.jpg").absolutePath

    override fun createProcessedImagePath(sessionId: String, suffix: String): String =
        File(temporaryFolder.root, "processed/$suffix.jpg").absolutePath

    override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

    override fun createPdfPath(fileName: String): String = File(temporaryFolder.root, "$fileName.pdf").absolutePath

    override fun createImageDocumentPath(fileName: String): String =
        File(temporaryFolder.root, "images/$fileName.jpg").absolutePath

    override fun createTempImagePath(suffix: String): String =
        File(temporaryFolder.root, "temp/$suffix.jpg").absolutePath

    override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun deleteFile(path: String): AppResult<Unit> {
        File(path).delete()
        return AppResult.Success(Unit)
    }

    override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> {
        paths.forEach { path -> File(path).delete() }
        return AppResult.Success(Unit)
    }

    override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> =
        deleteFiles(listOfNotNull(page.originalImagePath, page.processedImagePath, page.thumbnailPath))

    override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeImageProcessingRepository : ImageProcessingRepository {
    override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = AppResult.Success(null)

    override suspend fun evaluateQuality(inputPath: String, corners: PageCorners?): AppResult<ScanQualityAssessment> =
        AppResult.Success(ScanQualityAssessment.good())

    override suspend fun processPage(
        inputPath: String,
        processedOutputPath: String,
        thumbnailOutputPath: String,
        scanMode: ScanMode,
        corners: PageCorners?
    ): AppResult<ProcessedPageResult> = AppResult.Error("Not used.", AppErrorCategory.PROCESSING)

    override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
        AppResult.Success(outputPath)
}

private class FakeDocumentRepository : DocumentRepository {
    val documents = linkedMapOf<String, DoclyDocument>()
    private val documentsFlow = MutableStateFlow<List<DoclyDocument>>(emptyList())

    override fun observeDocuments(): Flow<List<DoclyDocument>> = documentsFlow

    override suspend fun getDocument(documentId: String): AppResult<DoclyDocument?> =
        AppResult.Success(documents[documentId])

    override suspend fun importDocument(uriString: String): AppResult<DoclyDocument> =
        AppResult.Error("Not used.", AppErrorCategory.VALIDATION)

    override suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit> {
        documents[document.id] = document
        documentsFlow.value = documents.values.toList()
        return AppResult.Success(Unit)
    }

    override suspend fun renameDocument(documentId: String, name: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
        documents.remove(documentId)
        documentsFlow.value = documents.values.toList()
        return AppResult.Success(Unit)
    }

    override suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun updateLastOpened(documentId: String): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakePdfRepository(private val temporaryFolder: TemporaryFolder) : PdfRepository {
    var pageImagePaths: List<String> = emptyList()

    override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
        this.pageImagePaths = pageImagePaths
        File(outputPdfPath).apply {
            parentFile?.mkdirs()
            writeText("pdf")
        }
        return AppResult.Success(outputPdfPath)
    }
}

private class FakeScanPageRenderer : ScanPageRenderer {
    val renderedPageIds = mutableListOf<String>()
    val renderedPaths = mutableListOf<String>()

    override suspend fun render(page: ScannedPage, outputPath: String): AppResult<String> {
        renderedPageIds += page.id
        renderedPaths += outputPath
        File(outputPath).apply {
            parentFile?.mkdirs()
            writeText("rendered-${page.id}")
        }
        return AppResult.Success(outputPath)
    }
}
