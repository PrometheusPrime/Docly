package com.docly.app.domain

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageCorners
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
import com.docly.app.domain.usecase.page.ApplyPageCropUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPageCropUseCaseTest {
    @Test
    fun applyCropProcessesManualCornersAndUpdatesExistingPage() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        val imageProcessingRepository = FakeImageProcessingRepository()
        val page = samplePage()
        val corners = sampleCorners()

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = FixedIdProvider("crop-1")
        )(page = page, corners = corners)

        val updatedPage = result.getOrNull()
        assertEquals(StorageReserveBytes.CAPTURE_BYTES, fileRepository.requiredBytes)
        assertEquals("/raw/page.jpg", imageProcessingRepository.processRequests.single().inputPath)
        assertEquals(
            "/processed/session-id/page-id_crop-1.jpg",
            imageProcessingRepository.processRequests.single().processedOutputPath
        )
        assertEquals(
            "/thumb/session-id/page-id_crop-1.jpg",
            imageProcessingRepository.processRequests.single().thumbnailOutputPath
        )
        assertEquals(ScanMode.DOCUMENT, imageProcessingRepository.processRequests.single().scanMode)
        assertEquals(corners, imageProcessingRepository.processRequests.single().corners)
        assertEquals(updatedPage, scanRepository.updatedPages.single())
        assertEquals("/processed/session-id/page-id_crop-1.jpg", updatedPage?.processedImagePath)
        assertEquals("/thumb/session-id/page-id_crop-1.jpg", updatedPage?.thumbnailPath)
        assertEquals(corners, updatedPage?.corners)
        assertEquals(1000, updatedPage?.width)
        assertEquals(1400, updatedPage?.height)
    }

    @Test
    fun processingFailureDeletesGeneratedPathsAndDoesNotUpdatePage() = runBlocking {
        val scanRepository = FakeScanRepository()
        val fileRepository = FakeFileRepository()
        val imageProcessingRepository = FakeImageProcessingRepository(
            processResult = AppResult.Error(
                message = "Perspective correction failed.",
                category = AppErrorCategory.PROCESSING
            )
        )

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            imageProcessingRepository = imageProcessingRepository,
            idProvider = FixedIdProvider("crop-1")
        )(page = samplePage(), corners = sampleCorners())

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertTrue(scanRepository.updatedPages.isEmpty())
        assertEquals(
            listOf("/processed/session-id/page-id_crop-1.jpg", "/thumb/session-id/page-id_crop-1.jpg"),
            fileRepository.deletedFiles
        )
    }

    @Test
    fun updateFailureDeletesGeneratedPathsAndKeepsExistingPageUnchanged() = runBlocking {
        val scanRepository = FakeScanRepository(
            updateResult = AppResult.Error(
                message = "Could not update page.",
                category = AppErrorCategory.STORAGE
            )
        )
        val fileRepository = FakeFileRepository()

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = fileRepository,
            idProvider = FixedIdProvider("crop-1")
        )(page = samplePage(), corners = sampleCorners())

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(1, scanRepository.updateAttempts)
        assertEquals(
            listOf("/processed/session-id/page-id_crop-1.jpg", "/thumb/session-id/page-id_crop-1.jpg"),
            fileRepository.deletedFiles
        )
    }

    @Test
    fun successfulCropDeletesPreviouslyReferencedProcessedAssets() = runBlocking {
        val fileRepository = FakeFileRepository()
        val page = samplePage(
            processedImagePath = "/processed/session-id/old.jpg",
            thumbnailPath = "/thumb/session-id/old.jpg"
        )

        val result = useCase(
            fileRepository = fileRepository,
            idProvider = FixedIdProvider("crop-2")
        )(page = page, corners = sampleCorners())

        assertEquals("/processed/session-id/page-id_crop-2.jpg", result.getOrNull()?.processedImagePath)
        assertEquals(
            listOf("/processed/session-id/old.jpg", "/thumb/session-id/old.jpg"),
            fileRepository.deletedFiles
        )
    }

    @Test
    fun storageFailurePreventsProcessing() = runBlocking {
        val scanRepository = FakeScanRepository()
        val imageProcessingRepository = FakeImageProcessingRepository()

        val result = useCase(
            scanRepository = scanRepository,
            fileRepository = FakeFileRepository(
                storageResult = AppResult.Error(
                    message = "Not enough app storage is available.",
                    category = AppErrorCategory.STORAGE
                )
            ),
            imageProcessingRepository = imageProcessingRepository
        )(page = samplePage(), corners = sampleCorners())

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertTrue(imageProcessingRepository.processRequests.isEmpty())
        assertTrue(scanRepository.updatedPages.isEmpty())
    }

    private fun useCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        imageProcessingRepository: FakeImageProcessingRepository = FakeImageProcessingRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        idProvider: IdProvider = FixedIdProvider("crop-id")
    ): ApplyPageCropUseCase = ApplyPageCropUseCase(
        scanRepository = scanRepository,
        imageProcessingRepository = imageProcessingRepository,
        fileRepository = fileRepository,
        idProvider = idProvider
    )

    private fun samplePage(processedImagePath: String? = null, thumbnailPath: String? = null): ScannedPage =
        ScannedPage(
            id = "page-id",
            sessionId = "session-id",
            pageIndex = 0,
            originalImagePath = "/raw/page.jpg",
            processedImagePath = processedImagePath,
            thumbnailPath = thumbnailPath,
            rotationDegrees = 0,
            scanMode = ScanMode.DOCUMENT,
            width = 1000,
            height = 1400,
            corners = null,
            createdAt = 1L
        )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 30f),
        topRight = PointFSerializable(900f, 40f),
        bottomRight = PointFSerializable(880f, 1300f),
        bottomLeft = PointFSerializable(30f, 1290f)
    )

    private class FakeScanRepository(private val updateResult: AppResult<Unit> = AppResult.Success(Unit)) :
        ScanRepository {
        val updatedPages: MutableList<ScannedPage> = mutableListOf()
        var updateAttempts = 0

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used in this test.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(null)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = AppResult.Success(null)

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> {
            updateAttempts += 1
            return when (updateResult) {
                is AppResult.Error -> updateResult

                is AppResult.Success -> {
                    updatedPages += page
                    AppResult.Success(Unit)
                }
            }
        }

        override suspend fun deletePage(pageId: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeImageProcessingRepository(private val processResult: AppResult<ProcessedPageResult>? = null) :
        ImageProcessingRepository {
        val processRequests: MutableList<ProcessRequest> = mutableListOf()

        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = AppResult.Success(null)

        override suspend fun processPage(
            inputPath: String,
            processedOutputPath: String,
            thumbnailOutputPath: String,
            scanMode: ScanMode,
            corners: PageCorners?
        ): AppResult<ProcessedPageResult> {
            processRequests += ProcessRequest(
                inputPath = inputPath,
                processedOutputPath = processedOutputPath,
                thumbnailOutputPath = thumbnailOutputPath,
                scanMode = scanMode,
                corners = corners
            )
            return processResult ?: AppResult.Success(
                ProcessedPageResult(
                    processedImagePath = processedOutputPath,
                    thumbnailPath = thumbnailOutputPath,
                    detectedCorners = corners,
                    width = 600,
                    height = 900
                )
            )
        }

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
            AppResult.Success(outputPath)
    }

    private data class ProcessRequest(
        val inputPath: String,
        val processedOutputPath: String,
        val thumbnailOutputPath: String,
        val scanMode: ScanMode,
        val corners: PageCorners?
    )

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

    private class FixedIdProvider(private val id: String) : IdProvider {
        override fun generateId(): String = id
    }
}
