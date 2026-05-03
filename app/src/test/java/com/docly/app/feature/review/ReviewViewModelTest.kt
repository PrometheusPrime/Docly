package com.docly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.LOW_STORAGE_USER_MESSAGE
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
import com.docly.app.domain.usecase.page.AcceptReviewedPageUseCase
import com.docly.app.domain.usecase.page.ApplyPageCropUseCase
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
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
            scanRepository = FakeScanRepository(page = samplePage(corners = corners, scanMode = ScanMode.MIXED))
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("page-id", state.currentPageId)
        assertEquals("/raw/page.jpg", state.rawImagePath)
        assertEquals(1000, state.imageWidth)
        assertEquals(1400, state.imageHeight)
        assertEquals(corners, state.detectedCorners)
        assertEquals(corners, state.editableCorners)
        assertEquals(ScanMode.MIXED, state.selectedScanMode)
        assertEquals(ScanMode.MIXED, state.appliedScanMode)
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
    fun loadSessionProcessesFirstPendingPage() = runTest {
        val acceptedPage = samplePage(id = "accepted-page", pageIndex = 0, reviewStatus = PageReviewStatus.ACCEPTED)
        val pendingPage = samplePage(
            id = "pending-page",
            pageIndex = 1,
            corners = sampleCorners(),
            reviewStatus = PageReviewStatus.PENDING
        )
        val scanRepository = FakeScanRepository(pages = listOf(acceptedPage, pendingPage))
        val viewModel = viewModel(
            scanRepository = scanRepository,
            idProvider = FixedIdProvider("crop-1")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pending-page", state.currentPageId)
        assertEquals("/processed/session-id/pending-page_crop-1.jpg", state.processedImagePath)
        assertEquals(PageReviewStatus.PENDING, scanRepository.updatedPages.single().reviewStatus)
        assertEquals(1, state.pendingPageCount)
        assertEquals(1, state.acceptedPageCount)
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
    fun scanModeChangeUpdatesSelectionOnlyUntilApply() = runTest {
        val scanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners()))
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnScanModeChanged(ScanMode.COLOR))

        val state = viewModel.uiState.value
        assertEquals(ScanMode.COLOR, state.selectedScanMode)
        assertEquals(ScanMode.DOCUMENT, state.appliedScanMode)
        assertTrue(state.hasPendingScanModeChange)
        assertTrue(scanRepository.updatedPages.isEmpty())
    }

    @Test
    fun applyCropPersistsSelectedScanMode() = runTest {
        val scanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners()))
        val viewModel = viewModel(
            scanRepository = scanRepository,
            idProvider = FixedIdProvider("crop-1")
        )
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnScanModeChanged(ScanMode.COLOR))
        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanMode.COLOR, scanRepository.updatedPages.single().scanMode)
        assertEquals(ScanMode.COLOR, state.selectedScanMode)
        assertEquals(ScanMode.COLOR, state.appliedScanMode)
        assertFalse(state.hasPendingScanModeChange)
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
    fun processingFailureAfterModeChangeLeavesPriorProcessedState() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(
                page = samplePage(
                    corners = sampleCorners(),
                    processedImagePath = "/processed/session-id/old.jpg",
                    thumbnailPath = "/thumb/session-id/old.jpg"
                )
            ),
            imageProcessingRepository = FakeImageProcessingRepository(
                processResult = AppResult.Error(
                    message = "Enhancement failed.",
                    category = AppErrorCategory.PROCESSING
                )
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnScanModeChanged(ScanMode.COLOR))
        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("We could not process this page. Please try again.", state.errorMessage)
        assertEquals("/processed/session-id/old.jpg", state.processedImagePath)
        assertEquals("/thumb/session-id/old.jpg", state.thumbnailPath)
        assertEquals(ScanMode.COLOR, state.selectedScanMode)
        assertEquals(ScanMode.DOCUMENT, state.appliedScanMode)
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

    @Test
    fun lowStorageProcessingFailureShowsSpecificUserMessage() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(page = samplePage(corners = sampleCorners())),
            fileRepository = FakeFileRepository(
                storageResult = AppResult.Error(
                    message = LOW_STORAGE_USER_MESSAGE,
                    category = AppErrorCategory.STORAGE
                )
            )
        )
        val effect = async { viewModel.uiEffect.first() }
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnReprocessClicked)
        advanceUntilIdle()

        assertEquals(LOW_STORAGE_USER_MESSAGE, viewModel.uiState.value.errorMessage)
        assertEquals(ReviewUiEffect.ShowToast(LOW_STORAGE_USER_MESSAGE), effect.await())
    }

    @Test
    fun toggleOriginalSwitchesPreviewState() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(
                page = samplePage(
                    corners = sampleCorners(),
                    processedImagePath = "/processed/page.jpg",
                    thumbnailPath = "/thumb/page.jpg"
                )
            )
        )
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnToggleOriginalClicked)
        assertTrue(viewModel.uiState.value.showOriginal)

        viewModel.onEvent(ReviewUiEvent.OnToggleOriginalClicked)
        assertFalse(viewModel.uiState.value.showOriginal)
    }

    @Test
    fun rotatePersistsNextRotationAndUpdatesState() = runTest {
        val scanRepository = FakeScanRepository(
            page = samplePage(
                corners = sampleCorners(),
                processedImagePath = "/processed/page.jpg",
                rotationDegrees = 270
            )
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnRotateClicked)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.rotationDegrees)
        assertEquals(0, scanRepository.updatedPages.single().rotationDegrees)
    }

    @Test
    fun acceptLoadsNextPendingPageWhenMoreReviewPagesExist() = runTest {
        val firstPending = samplePage(
            id = "first-pending",
            pageIndex = 0,
            corners = sampleCorners(),
            processedImagePath = "/processed/first.jpg",
            reviewStatus = PageReviewStatus.PENDING
        )
        val secondPending = samplePage(
            id = "second-pending",
            pageIndex = 1,
            corners = sampleCorners(),
            processedImagePath = "/processed/second.jpg",
            reviewStatus = PageReviewStatus.PENDING
        )
        val scanRepository = FakeScanRepository(pages = listOf(firstPending, secondPending))
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(ReviewUiEvent.OnAcceptClicked)
        advanceUntilIdle()

        assertEquals("second-pending", viewModel.uiState.value.currentPageId)
        assertEquals(PageReviewStatus.ACCEPTED, scanRepository.updatedPages.single().reviewStatus)
        assertEquals(1, viewModel.uiState.value.pendingPageCount)
        assertEquals(1, viewModel.uiState.value.acceptedPageCount)
    }

    @Test
    fun acceptNavigatesToEditorWhenNoPendingPagesRemain() = runTest {
        val scanRepository = FakeScanRepository(
            page = samplePage(
                corners = sampleCorners(),
                processedImagePath = "/processed/page.jpg",
                reviewStatus = PageReviewStatus.PENDING
            )
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ReviewUiEvent.OnAcceptClicked)
        advanceUntilIdle()

        assertEquals(ReviewUiEffect.NavigateToEditor("session-id"), effect.await())
        assertEquals(PageReviewStatus.ACCEPTED, scanRepository.updatedPages.single().reviewStatus)
    }

    @Test
    fun rescanDeletesPendingPageAndNavigatesBackToScannerWhenNoPendingPagesRemain() = runTest {
        val scanRepository = FakeScanRepository(
            page = samplePage(
                corners = sampleCorners(),
                processedImagePath = "/processed/page.jpg",
                reviewStatus = PageReviewStatus.PENDING
            )
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ReviewUiEvent.OnRescanClicked)
        advanceUntilIdle()

        assertEquals(listOf("page-id"), scanRepository.deletedPageIds)
        assertEquals(ReviewUiEffect.NavigateBackToScanner("session-id"), effect.await())
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
        ),
        acceptReviewedPageUseCase = AcceptReviewedPageUseCase(scanRepository),
        deletePageUseCase = DeletePageUseCase(scanRepository),
        rotatePageUseCase = RotatePageUseCase(scanRepository)
    )

    private fun sampleSession(page: ScannedPage): ScanSession = ScanSession(
        id = "session-id",
        createdAt = 1L,
        updatedAt = 1L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = ScanMode.DOCUMENT,
        pages = listOf(page)
    )

    private fun samplePage(
        id: String = "page-id",
        pageIndex: Int = 0,
        corners: PageCorners? = null,
        scanMode: ScanMode = ScanMode.DOCUMENT,
        processedImagePath: String? = null,
        thumbnailPath: String? = null,
        rotationDegrees: Int = 0,
        reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/raw/page.jpg",
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
        rotationDegrees = rotationDegrees,
        scanMode = scanMode,
        width = 1000,
        height = 1400,
        corners = corners,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 30f),
        topRight = PointFSerializable(900f, 40f),
        bottomRight = PointFSerializable(880f, 1300f),
        bottomLeft = PointFSerializable(30f, 1290f)
    )

    private inner class FakeScanRepository(
        page: ScannedPage = samplePage(corners = sampleCorners()),
        pages: List<ScannedPage> = listOf(page),
        private val updateResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        private var session = sampleSession(page).copy(pages = pages)
        val updatedPages: MutableList<ScannedPage> = mutableListOf()
        val deletedPageIds: MutableList<String> = mutableListOf()

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
                session = session.copy(
                    pages = session.pages.map { existingPage ->
                        if (existingPage.id == page.id) page else existingPage
                    }
                )
                AppResult.Success(Unit)
            }
        }

        override suspend fun deletePage(pageId: String): AppResult<Unit> {
            deletedPageIds += pageId
            session = session.copy(pages = session.pages.filterNot { page -> page.id == pageId })
            return AppResult.Success(Unit)
        }

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

    private class FakeFileRepository(private val storageResult: AppResult<Unit> = AppResult.Success(Unit)) :
        FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = storageResult

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
