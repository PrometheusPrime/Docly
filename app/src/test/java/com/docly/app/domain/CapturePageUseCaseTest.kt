package com.docly.app.domain

import com.docly.app.core.camera.CameraCaptureResult
import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import com.docly.app.domain.usecase.page.CapturePageUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePageUseCaseTest {
    @Test
    fun captureCreatesNewSessionAndPersistsRawPage() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        var captureOutputPath = ""

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            idProvider = SequenceIdProvider(listOf("page-1")),
            timeProvider = FixedTimeProvider(timestampMillis = 123L)
        )(
            sessionId = null,
            scanMode = ScanMode.COLOR
        ) { outputPath ->
            captureOutputPath = outputPath
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 1000, height = 1500))
        }

        val page = result.getOrNull()?.page
        assertEquals("created-session", result.getOrNull()?.sessionId)
        assertEquals(1, scanRepository.createSessionCalls)
        assertEquals(ScanMode.COLOR, scanRepository.createdScanMode)
        assertEquals(StorageReserveBytes.CAPTURE_BYTES, fileRepository.requiredBytes)
        assertEquals("/raw/created-session/page-1.jpg", captureOutputPath)
        assertEquals("/raw/created-session/page-1.jpg", page?.originalImagePath)
        assertEquals("/thumb/created-session/page-1.jpg", page?.thumbnailPath)
        assertEquals(0, page?.pageIndex)
        assertEquals(1000, page?.width)
        assertEquals(1500, page?.height)
        assertEquals(123L, page?.createdAt)
        assertEquals(PageReviewStatus.PENDING, page?.reviewStatus)
        assertEquals(listOf("page-1"), scanRepository.addedPages.map { it.id })
    }

    @Test
    fun captureReusesLatestInProgressSessionAndAssignsNextPageIndex() = runBlocking {
        val existingPage = samplePage(id = "existing-page", sessionId = "latest-session", pageIndex = 4)
        val scanRepository = FakeScanRepository(
            latestSession = sampleSession(id = "latest-session", pages = listOf(existingPage))
        )

        val result = useCase(
            scanRepository = scanRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.MIXED
        ) { outputPath ->
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 300, height = 400))
        }

        val page = result.getOrNull()?.page
        assertEquals("latest-session", result.getOrNull()?.sessionId)
        assertEquals(0, scanRepository.createSessionCalls)
        assertEquals(5, page?.pageIndex)
        assertEquals(ScanMode.MIXED, page?.scanMode)
        assertEquals("/raw/latest-session/page-1.jpg", page?.originalImagePath)
        assertEquals("/thumb/latest-session/page-1.jpg", page?.thumbnailPath)
    }

    @Test
    fun storageFailurePreventsCapture() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository(
            storageResult = AppResult.Error(
                message = "Not enough app storage is available.",
                category = AppErrorCategory.STORAGE
            )
        )
        var captureCalls = 0

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) {
            captureCalls += 1
            AppResult.Success(CameraCaptureResult(path = it, width = 100, height = 200))
        }

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(0, captureCalls)
        assertEquals(0, scanRepository.createSessionCalls)
        assertTrue(scanRepository.addedPages.isEmpty())
    }

    @Test
    fun captureFailureDeletesPartialOutputAndDoesNotInsertPage() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) {
            AppResult.Error(
                message = "Could not capture image. Please try again.",
                category = AppErrorCategory.CAMERA
            )
        }

        assertEquals(AppErrorCategory.CAMERA, result.errorOrNull()?.category)
        assertEquals(listOf("/raw/created-session/page-1.jpg"), fileRepository.deletedFiles)
        assertTrue(scanRepository.addedPages.isEmpty())
    }

    @Test
    fun thumbnailFailureDeletesRawAndThumbnailFilesAndDoesNotInsertPage() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        val imageProcessingRepository = FakeImageProcessingRepository(
            thumbnailResult = AppResult.Error(
                message = "Thumbnail could not be generated.",
                category = AppErrorCategory.PROCESSING
            )
        )

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) { outputPath ->
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 100, height = 200))
        }

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertEquals(
            listOf("/raw/created-session/page-1.jpg", "/thumb/created-session/page-1.jpg"),
            fileRepository.deletedFiles
        )
        assertTrue(scanRepository.addedPages.isEmpty())
        assertTrue(imageProcessingRepository.detectedInputPaths.isEmpty())
    }

    @Test
    fun capturePersistsDetectedDocumentCorners() = runBlocking {
        val detectedCorners = sampleCorners()
        val scanRepository = FakeScanRepository()

        val result = useCase(
            scanRepository = scanRepository,
            imageProcessingRepository = FakeImageProcessingRepository(
                detectionResult = AppResult.Success(detectedCorners)
            ),
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) { outputPath ->
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 100, height = 200))
        }

        assertEquals(detectedCorners, result.getOrNull()?.page?.corners)
        assertEquals(detectedCorners, scanRepository.addedPages.single().corners)
    }

    @Test
    fun documentDetectionFailureStillPersistsCapturedPageWithoutCorners() = runBlocking {
        val scanRepository = FakeScanRepository()

        val result = useCase(
            scanRepository = scanRepository,
            imageProcessingRepository = FakeImageProcessingRepository(
                detectionResult = AppResult.Error(
                    message = "Document boundary detection failed.",
                    category = AppErrorCategory.PROCESSING
                )
            ),
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) { outputPath ->
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 100, height = 200))
        }

        assertEquals(null, result.errorOrNull())
        assertEquals(null, result.getOrNull()?.page?.corners)
        assertEquals(null, scanRepository.addedPages.single().corners)
    }

    @Test
    fun addPageFailureDeletesCapturedRawFile() = runBlocking {
        val scanRepository = FakeScanRepository().apply {
            addPageErrorsById["page-1"] = AppResult.Error(
                message = "Could not add page.",
                category = AppErrorCategory.STORAGE
            )
        }
        val fileRepository = FakeFileRepository()

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            scanMode = ScanMode.DOCUMENT
        ) { outputPath ->
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 100, height = 200))
        }

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(
            listOf("/raw/created-session/page-1.jpg", "/thumb/created-session/page-1.jpg"),
            fileRepository.deletedFiles
        )
        assertTrue(scanRepository.addedPages.isEmpty())
    }

    private fun useCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        imageProcessingRepository: ImageProcessingRepository = FakeImageProcessingRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1", "page-2")),
        timeProvider: TimeProvider = FixedTimeProvider(timestampMillis = 1L)
    ): CapturePageUseCase = CapturePageUseCase(
        scanRepository = scanRepository,
        fileRepository = fileRepository,
        imageProcessingRepository = imageProcessingRepository,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private fun sampleSession(
        id: String = "session-id",
        scanMode: ScanMode = ScanMode.DOCUMENT,
        pages: List<ScannedPage> = emptyList()
    ): ScanSession = ScanSession(
        id = id,
        createdAt = 1L,
        updatedAt = 1L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = scanMode,
        pages = pages
    )

    private fun samplePage(id: String, sessionId: String = "session-id", pageIndex: Int): ScannedPage = ScannedPage(
        id = id,
        sessionId = sessionId,
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(10f, 20f),
        topRight = PointFSerializable(90f, 25f),
        bottomRight = PointFSerializable(80f, 180f),
        bottomLeft = PointFSerializable(15f, 170f)
    )

    private class FakeScanRepository(var latestSession: ScanSession? = null) : ScanRepository {
        val sessionsById: MutableMap<String, ScanSession> = mutableMapOf()
        val addedPages: MutableList<ScannedPage> = mutableListOf()
        val addPageErrorsById: MutableMap<String, AppResult.Error> = mutableMapOf()
        var createSessionCalls = 0
        var createdScanMode: ScanMode? = null

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> {
            createSessionCalls += 1
            createdScanMode = scanMode
            val session = ScanSession(
                id = "created-session",
                createdAt = 1L,
                updatedAt = 1L,
                status = ScanSessionStatus.IN_PROGRESS,
                scanMode = scanMode
            )
            sessionsById[session.id] = session
            return AppResult.Success(session)
        }

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> =
            AppResult.Success(sessionsById[sessionId])

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = AppResult.Success(latestSession)

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> {
            addPageErrorsById[page.id]?.let { error -> return error }
            addedPages += page
            return AppResult.Success(Unit)
        }

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePage(pageId: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeFileRepository(private val storageResult: AppResult<Unit> = AppResult.Success(Unit)) :
        FileRepository {
        val deletedFiles: MutableList<String> = mutableListOf()
        var requiredBytes: Long? = null

        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> {
            this.requiredBytes = requiredBytes
            return storageResult
        }

        override suspend fun deleteFile(path: String): AppResult<Unit> {
            deletedFiles += path
            return AppResult.Success(Unit)
        }

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> {
            deletedFiles += paths
            return AppResult.Success(Unit)
        }

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
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

    private class FakeImageProcessingRepository(
        private val thumbnailResult: AppResult<String> = AppResult.Success(""),
        private val detectionResult: AppResult<PageCorners?> = AppResult.Success(null)
    ) : ImageProcessingRepository {
        val detectedInputPaths: MutableList<String> = mutableListOf()

        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> {
            detectedInputPaths += inputPath
            return detectionResult
        }

        override suspend fun processPage(
            inputPath: String,
            processedOutputPath: String,
            thumbnailOutputPath: String,
            scanMode: ScanMode,
            corners: PageCorners?
        ): AppResult<ProcessedPageResult> = AppResult.Error(
            message = "Not implemented.",
            category = AppErrorCategory.PROCESSING
        )

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
            when (thumbnailResult) {
                is AppResult.Error -> thumbnailResult
                is AppResult.Success -> AppResult.Success(outputPath)
            }
    }
}
