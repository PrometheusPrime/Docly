package com.docly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
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
import com.docly.app.domain.usecase.page.ApplyPageCropUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadSessionUsesDetectedCornersWhenPresent() = runTest {
        val corners = sampleCorners()
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(page = samplePage(corners = corners))
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("page-id", state.currentPageId)
        assertEquals("/raw/page.jpg", state.rawImagePath)
        assertEquals(1000, state.imageWidth)
        assertEquals(1400, state.imageHeight)
        assertEquals(corners, state.detectedCorners)
        assertEquals(corners, state.editableCorners)
        assertFalse(state.isProcessing)
    }

    @Test
    fun loadSessionFallsBackToFullImageCornersWhenDetectionIsMissing() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(page = samplePage(corners = null))
        )
        advanceUntilIdle()

        assertEquals(fullImageCorners(imageWidth = 1000, imageHeight = 1400), viewModel.uiState.value.editableCorners)
        assertEquals(null, viewModel.uiState.value.detectedCorners)
    }

    @Test
    fun cornerChangeAndResetEventsUpdateEditableCorners() = runTest {
        val detectedCorners = sampleCorners()
        val editedCorners = detectedCorners.copy(topLeft = PointFSerializable(1f, 2f))
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(page = samplePage(corners = detectedCorners))
        )
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnCornersChanged(editedCorners))
        assertEquals(editedCorners, viewModel.uiState.value.editableCorners)

        viewModel.onEvent(ReviewUiEvent.OnResetToDetectedClicked)
        assertEquals(detectedCorners, viewModel.uiState.value.editableCorners)

        viewModel.onEvent(ReviewUiEvent.OnResetToFullImageClicked)
        assertEquals(fullImageCorners(imageWidth = 1000, imageHeight = 1400), viewModel.uiState.value.editableCorners)
    }

    @Test
    fun applyCropSuccessUpdatesStateAndEmitsToast() = runTest {
        val scanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners()))
        val viewModel = viewModel(
            scanRepository = scanRepository,
            idProvider = FixedIdProvider("crop-1")
        )
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/processed/session-id/page-id_crop-1.jpg", state.processedImagePath)
        assertEquals("/thumb/session-id/page-id_crop-1.jpg", state.thumbnailPath)
        assertEquals(sampleCorners(), state.editableCorners)
        assertEquals(scanRepository.updatedPages.single().processedImagePath, state.processedImagePath)
        assertEquals(ReviewUiEffect.ShowToast("Crop applied."), effect.await())
    }

    @Test
    fun processingFailureShowsReadableErrorAndLeavesExistingPageState() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners())),
            imageProcessingRepository = FakeImageProcessingRepository(
                processResult = AppResult.Error(
                    message = "Perspective correction failed.",
                    category = AppErrorCategory.PROCESSING
                )
            )
        )
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        assertEquals("We could not process this page. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(null, viewModel.uiState.value.processedImagePath)
        assertTrue(effect.await() is ReviewUiEffect.ShowToast)
    }

    @Test
    fun updateFailureShowsReadableErrorAndLeavesExistingPageState() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(
                page = samplePage(corners = sampleCorners()),
                updateResult = AppResult.Error(
                    message = "Could not update page.",
                    category = AppErrorCategory.STORAGE
                )
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        assertEquals("We could not save or load this file. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(null, viewModel.uiState.value.processedImagePath)
    }

    private fun viewModel(
        scanRepository: FakeScanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners())),
        imageProcessingRepository: FakeImageProcessingRepository = FakeImageProcessingRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        idProvider: IdProvider = FixedIdProvider("crop-id")
    ): ReviewViewModel = ReviewViewModel(
        savedStateHandle = SavedStateHandle(mapOf(SESSION_ID_KEY to "session-id")),
        getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
        applyPageCropUseCase = ApplyPageCropUseCase(
            scanRepository = scanRepository,
            imageProcessingRepository = imageProcessingRepository,
            fileRepository = fileRepository,
            idProvider = idProvider
        )
    )

    private fun sampleSession(page: ScannedPage): ScanSession = ScanSession(
        id = "session-id",
        createdAt = 1L,
        updatedAt = 1L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = ScanMode.DOCUMENT,
        pages = listOf(page)
    )

    private fun samplePage(corners: PageCorners?): ScannedPage = ScannedPage(
        id = "page-id",
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = "/raw/page.jpg",
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 1000,
        height = 1400,
        corners = corners,
        createdAt = 1L
    )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 30f),
        topRight = PointFSerializable(900f, 40f),
        bottomRight = PointFSerializable(880f, 1300f),
        bottomLeft = PointFSerializable(30f, 1290f)
    )

    private inner class FakeScanRepository(
        page: ScannedPage,
        private val updateResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        private var session = sampleSession(page)
        val updatedPages: MutableList<ScannedPage> = mutableListOf()

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used in this test.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(session)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = AppResult.Success(session)

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = when (updateResult) {
            is AppResult.Error -> updateResult

            is AppResult.Success -> {
                updatedPages += page
                session = session.copy(pages = listOf(page))
                AppResult.Success(Unit)
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
        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = AppResult.Success(null)

        override suspend fun processPage(
            inputPath: String,
            processedOutputPath: String,
            thumbnailOutputPath: String,
            scanMode: ScanMode,
            corners: PageCorners?
        ): AppResult<ProcessedPageResult> = processResult ?: AppResult.Success(
            ProcessedPageResult(
                processedImagePath = processedOutputPath,
                thumbnailPath = thumbnailOutputPath,
                detectedCorners = corners,
                width = 600,
                height = 900
            )
        )

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
            AppResult.Success(outputPath)
    }

    private class FakeFileRepository : FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FixedIdProvider(private val id: String) : IdProvider {
        override fun generateId(): String = id
    }

    class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
        TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
