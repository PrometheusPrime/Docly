package com.docly.app.domain

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import com.docly.app.domain.usecase.page.ImportDevicePhotosUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDevicePhotosUseCaseTest {
    @Test
    fun emptySelectionReturnsValidationWithoutSideEffects() = runBlocking {
        val scanRepository = FakeScanRepository()
        val devicePhotoRepository = FakeDevicePhotoRepository()
        val result = useCase(
            scanRepository = scanRepository,
            devicePhotoRepository = devicePhotoRepository
        )(
            sessionId = null,
            sourceUris = listOf("  "),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
        assertEquals(0, scanRepository.createSessionCalls)
        assertTrue(scanRepository.addedPages.isEmpty())
        assertTrue(devicePhotoRepository.requests.isEmpty())
    }

    @Test
    fun importCreatesNewSessionWhenNoneExists() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first"),
            scanMode = ScanMode.COLOR
        )

        val importedPages = result.getOrNull()?.importedPages.orEmpty()
        assertEquals("created-session", result.getOrNull()?.sessionId)
        assertEquals(1, scanRepository.createSessionCalls)
        assertEquals(ScanMode.COLOR, scanRepository.createdScanMode)
        assertEquals(StorageReserveBytes.CAPTURE_BYTES, fileRepository.requiredBytes)
        assertEquals(1, importedPages.size)
        assertEquals("page-1", importedPages.first().id)
        assertEquals("created-session", importedPages.first().sessionId)
        assertEquals(0, importedPages.first().pageIndex)
        assertEquals("/raw/page-1.jpg", importedPages.first().originalImagePath)
        assertEquals("/thumb/created-session/page-1.jpg", importedPages.first().thumbnailPath)
        assertEquals(100, importedPages.first().width)
        assertEquals(200, importedPages.first().height)
        assertEquals(PageReviewStatus.PENDING, importedPages.first().reviewStatus)
    }

    @Test
    fun importReusesLatestInProgressSessionWhenNoSessionIdIsProvided() = runBlocking {
        val existingPage = samplePage(id = "existing-page", pageIndex = 2)
        val scanRepository = FakeScanRepository(
            latestSession = sampleSession(id = "latest-session", pages = listOf(existingPage))
        )

        val result = useCase(
            scanRepository = scanRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first"),
            scanMode = ScanMode.MIXED
        )

        val importedPage = result.getOrNull()?.importedPages?.single()
        assertEquals("latest-session", result.getOrNull()?.sessionId)
        assertEquals(0, scanRepository.createSessionCalls)
        assertEquals(3, importedPage?.pageIndex)
        assertEquals(ScanMode.MIXED, importedPage?.scanMode)
    }

    @Test
    fun importPreservesPickerOrderAndPageIndexOrder() = runBlocking {
        val scanRepository = FakeScanRepository(
            latestSession = sampleSession(
                id = "latest-session",
                pages = listOf(samplePage(id = "existing-page", pageIndex = 5))
            )
        )
        val devicePhotoRepository = FakeDevicePhotoRepository().apply {
            importedImagesByUri["content://first"] = ImportedRawImage("/raw/first.png", width = 10, height = 20)
            importedImagesByUri["content://second"] = ImportedRawImage("/raw/second.png", width = 30, height = 40)
        }

        val result = useCase(
            scanRepository = scanRepository,
            devicePhotoRepository = devicePhotoRepository,
            idProvider = SequenceIdProvider(listOf("page-1", "page-2")),
            timeProvider = FixedTimeProvider(timestampMillis = 123L)
        )(
            sessionId = null,
            sourceUris = listOf("content://first", "content://second"),
            scanMode = ScanMode.DOCUMENT
        )

        val importedPages = result.getOrNull()?.importedPages.orEmpty()
        assertEquals(listOf("content://first", "content://second"), devicePhotoRepository.requests.map { it.sourceUri })
        assertEquals(listOf("page-1", "page-2"), importedPages.map { page -> page.id })
        assertEquals(listOf(6, 7), importedPages.map { page -> page.pageIndex })
        assertEquals(listOf("/raw/first.png", "/raw/second.png"), importedPages.map { page -> page.originalImagePath })
        assertEquals(
            listOf("/thumb/latest-session/page-1.jpg", "/thumb/latest-session/page-2.jpg"),
            importedPages.map { page -> page.thumbnailPath }
        )
        assertEquals(listOf(123L, 123L), importedPages.map { page -> page.createdAt })
    }

    @Test
    fun importFailureRollsBackPagesCreatedByCurrentBatch() = runBlocking {
        val scanRepository = FakeScanRepository()
        val devicePhotoRepository = FakeDevicePhotoRepository().apply {
            errorsByUri["content://second"] = AppResult.Error(
                message = "Copy failed.",
                category = AppErrorCategory.STORAGE
            )
        }

        val result = useCase(
            scanRepository = scanRepository,
            devicePhotoRepository = devicePhotoRepository,
            idProvider = SequenceIdProvider(listOf("page-1", "page-2"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first", "content://second"),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(listOf("page-1"), scanRepository.deletedPageIds)
        assertEquals(listOf("page-1"), scanRepository.addedPages.map { page -> page.id })
    }

    @Test
    fun addPageFailureDeletesUnpersistedImportedFile() = runBlocking {
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
            sourceUris = listOf("content://first"),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(listOf("/raw/page-1.jpg", "/thumb/created-session/page-1.jpg"), fileRepository.deletedFiles)
        assertTrue(scanRepository.addedPages.isEmpty())
    }

    @Test
    fun thumbnailFailureDeletesCurrentFilesAndRollsBackPriorImportedPages() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        val imageProcessingRepository = FakeImageProcessingRepository().apply {
            errorsByInputPath["/raw/page-2.jpg"] = AppResult.Error(
                message = "Thumbnail could not be generated.",
                category = AppErrorCategory.PROCESSING
            )
        }

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = SequenceIdProvider(listOf("page-1", "page-2"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first", "content://second"),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertEquals(listOf("page-1"), scanRepository.deletedPageIds)
        assertEquals(
            listOf("/raw/page-2.jpg", "/thumb/created-session/page-2.jpg"),
            fileRepository.deletedFiles
        )
        assertEquals(listOf("/raw/page-1.jpg"), imageProcessingRepository.detectedInputPaths)
    }

    @Test
    fun importPersistsDetectedDocumentCorners() = runBlocking {
        val detectedCorners = sampleCorners()
        val scanRepository = FakeScanRepository()
        val imageProcessingRepository = FakeImageProcessingRepository().apply {
            detectionResultsByInputPath["/raw/page-1.jpg"] = AppResult.Success(detectedCorners)
        }

        val result = useCase(
            scanRepository = scanRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first"),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(detectedCorners, result.getOrNull()?.importedPages?.single()?.corners)
        assertEquals(detectedCorners, scanRepository.addedPages.single().corners)
    }

    @Test
    fun documentDetectionFailureStillPersistsImportedPageWithoutCorners() = runBlocking {
        val scanRepository = FakeScanRepository()
        val imageProcessingRepository = FakeImageProcessingRepository().apply {
            detectionResultsByInputPath["/raw/page-1.jpg"] = AppResult.Error(
                message = "Document boundary detection failed.",
                category = AppErrorCategory.PROCESSING
            )
        }

        val result = useCase(
            scanRepository = scanRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = SequenceIdProvider(listOf("page-1"))
        )(
            sessionId = null,
            sourceUris = listOf("content://first"),
            scanMode = ScanMode.DOCUMENT
        )

        assertEquals(null, result.errorOrNull())
        assertEquals(null, result.getOrNull()?.importedPages?.single()?.corners)
        assertEquals(null, scanRepository.addedPages.single().corners)
    }

    private fun useCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        devicePhotoRepository: FakeDevicePhotoRepository = FakeDevicePhotoRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        imageProcessingRepository: ImageProcessingRepository = FakeImageProcessingRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1", "page-2", "page-3")),
        timeProvider: TimeProvider = FixedTimeProvider(timestampMillis = 1L)
    ): ImportDevicePhotosUseCase = ImportDevicePhotosUseCase(
        scanRepository = scanRepository,
        devicePhotoRepository = devicePhotoRepository,
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

    private fun samplePage(id: String, pageIndex: Int): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
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
        val deletedPageIds: MutableList<String> = mutableListOf()
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

        override suspend fun deletePage(pageId: String): AppResult<Unit> {
            deletedPageIds += pageId
            return AppResult.Success(Unit)
        }

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeDevicePhotoRepository : DevicePhotoRepository {
        val importedImagesByUri: MutableMap<String, ImportedRawImage> = mutableMapOf()
        val errorsByUri: MutableMap<String, AppResult.Error> = mutableMapOf()
        val requests: MutableList<ImportRequest> = mutableListOf()

        override suspend fun importRawPhoto(
            sessionId: String,
            pageId: String,
            sourceUri: String
        ): AppResult<ImportedRawImage> {
            requests += ImportRequest(sessionId = sessionId, pageId = pageId, sourceUri = sourceUri)
            errorsByUri[sourceUri]?.let { error -> return error }
            return AppResult.Success(
                importedImagesByUri[sourceUri] ?: ImportedRawImage(
                    path = "/raw/$pageId.jpg",
                    width = 100,
                    height = 200
                )
            )
        }
    }

    private data class ImportRequest(val sessionId: String, val pageId: String, val sourceUri: String)

    private class FakeFileRepository : FileRepository {
        val deletedFiles: MutableList<String> = mutableListOf()
        var requiredBytes: Long? = null
        var storageAvailabilityResult: AppResult<Unit> = AppResult.Success(Unit)

        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> {
            this.requiredBytes = requiredBytes
            return storageAvailabilityResult
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

    private class FakeImageProcessingRepository : ImageProcessingRepository {
        val errorsByInputPath: MutableMap<String, AppResult.Error> = mutableMapOf()
        val detectionResultsByInputPath: MutableMap<String, AppResult<PageCorners?>> = mutableMapOf()
        val detectedInputPaths: MutableList<String> = mutableListOf()

        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> {
            detectedInputPaths += inputPath
            return detectionResultsByInputPath[inputPath] ?: AppResult.Success(null)
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

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> {
            errorsByInputPath[inputPath]?.let { error -> return error }
            return AppResult.Success(outputPath)
        }
    }
}
