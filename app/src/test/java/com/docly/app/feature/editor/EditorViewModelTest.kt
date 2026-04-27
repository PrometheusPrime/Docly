package com.docly.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadSessionShowsAcceptedPagesOnlyInPageOrderAndCountsPendingPages() = runTest {
        val pendingPage = samplePage(id = "pending-page", pageIndex = 0, reviewStatus = PageReviewStatus.PENDING)
        val secondAccepted = samplePage(id = "second-accepted", pageIndex = 2)
        val firstAccepted = samplePage(id = "first-accepted", pageIndex = 1)
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(
                pages = listOf(pendingPage, secondAccepted, firstAccepted)
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("first-accepted", "second-accepted"), viewModel.uiState.value.pages.map { page -> page.id })
        assertEquals(1, viewModel.uiState.value.pendingPageCount)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun moveAcceptedPageDownReordersFullSessionIdsAndPreservesPendingPageSlot() = runTest {
        val scanRepository = FakeScanRepository(
            pages = listOf(
                samplePage(id = "accepted-a", pageIndex = 0),
                samplePage(id = "pending-page", pageIndex = 1, reviewStatus = PageReviewStatus.PENDING),
                samplePage(id = "accepted-b", pageIndex = 2)
            )
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.OnMovePageDown("accepted-a"))
        advanceUntilIdle()

        assertEquals(listOf("accepted-b", "pending-page", "accepted-a"), scanRepository.reorderedPageIds.single())
        assertEquals(listOf("accepted-b", "accepted-a"), viewModel.uiState.value.pages.map { page -> page.id })
        assertEquals(1, viewModel.uiState.value.pendingPageCount)
    }

    @Test
    fun moveAcceptedPageUpNoOpsAtVisibleBoundary() = runTest {
        val scanRepository = FakeScanRepository(
            pages = listOf(
                samplePage(id = "accepted-a", pageIndex = 0),
                samplePage(id = "accepted-b", pageIndex = 1)
            )
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.OnMovePageUp("accepted-a"))
        advanceUntilIdle()

        assertTrue(scanRepository.reorderedPageIds.isEmpty())
        assertEquals(listOf("accepted-a", "accepted-b"), viewModel.uiState.value.pages.map { page -> page.id })
    }

    @Test
    fun rotatePagePersistsNextRotationAndReloadsState() = runTest {
        val scanRepository = FakeScanRepository(
            pages = listOf(samplePage(id = "page-id", pageIndex = 0, rotationDegrees = 270))
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()

        viewModel.onEvent(EditorUiEvent.OnRotatePageClicked("page-id"))
        advanceUntilIdle()

        assertEquals(0, scanRepository.updatedPages.single().rotationDegrees)
        assertEquals(0, viewModel.uiState.value.pages.single().rotationDegrees)
    }

    @Test
    fun deletePageReloadsStateEvenWhenCleanupReportsError() = runTest {
        val scanRepository = FakeScanRepository(
            pages = listOf(samplePage(id = "page-id", pageIndex = 0)),
            deleteResult = AppResult.Error("Cleanup failed.", AppErrorCategory.STORAGE)
        )
        val viewModel = viewModel(scanRepository = scanRepository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(EditorUiEvent.OnDeletePageClicked("page-id"))
        advanceUntilIdle()

        assertEquals(listOf("page-id"), scanRepository.deletedPageIds)
        assertEquals(emptyList<ScannedPage>(), viewModel.uiState.value.pages)
        assertEquals("We could not save or load this file. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(
            EditorUiEffect.ShowToast("We could not save or load this file. Please try again."),
            effect.await()
        )
    }

    @Test
    fun addPageEmitsScannerNavigationForCurrentSession() = runTest {
        val viewModel = viewModel(scanRepository = FakeScanRepository(pages = listOf(samplePage())))
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(EditorUiEvent.OnAddPageClicked)
        advanceUntilIdle()

        assertEquals(EditorUiEffect.NavigateToScanner("session-id"), effect.await())
    }

    @Test
    fun continueEmitsMetadataNavigationOnlyWhenAcceptedPagesAreReady() = runTest {
        val viewModel = viewModel(scanRepository = FakeScanRepository(pages = listOf(samplePage())))
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(EditorUiEvent.OnContinueClicked)
        advanceUntilIdle()

        assertEquals(EditorUiEffect.NavigateToMetadata("session-id"), effect.await())
    }

    @Test
    fun continueIsBlockedWhenPendingPagesRemain() = runTest {
        val viewModel = viewModel(
            scanRepository = FakeScanRepository(
                pages = listOf(
                    samplePage(id = "accepted-page", pageIndex = 0),
                    samplePage(id = "pending-page", pageIndex = 1, reviewStatus = PageReviewStatus.PENDING)
                )
            )
        )
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(EditorUiEvent.OnContinueClicked)
        advanceUntilIdle()

        assertEquals("Review all pending pages before continuing.", viewModel.uiState.value.errorMessage)
        assertEquals(EditorUiEffect.ShowToast("Review all pending pages before continuing."), effect.await())
    }

    private fun viewModel(scanRepository: FakeScanRepository): EditorViewModel = EditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(SESSION_ID_KEY to "session-id")),
        getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
        deletePageUseCase = DeletePageUseCase(scanRepository),
        reorderPagesUseCase = ReorderPagesUseCase(scanRepository),
        rotatePageUseCase = RotatePageUseCase(scanRepository)
    )

    private fun samplePage(
        id: String = "page-id",
        pageIndex: Int = 0,
        rotationDegrees: Int = 0,
        reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = "/processed/$id.jpg",
        thumbnailPath = "/thumb/$id.jpg",
        rotationDegrees = rotationDegrees,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private inner class FakeScanRepository(
        pages: List<ScannedPage>,
        private val deleteResult: AppResult<Unit> = AppResult.Success(Unit),
        private val updateResult: AppResult<Unit> = AppResult.Success(Unit),
        private val reorderResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        private var session = ScanSession(
            id = "session-id",
            createdAt = 1L,
            updatedAt = 1L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = ScanMode.DOCUMENT,
            pages = pages
        )
        val updatedPages: MutableList<ScannedPage> = mutableListOf()
        val deletedPageIds: MutableList<String> = mutableListOf()
        val reorderedPageIds: MutableList<List<String>> = mutableListOf()

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(session)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

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
            return deleteResult
        }

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            when (reorderResult) {
                is AppResult.Error -> reorderResult

                is AppResult.Success -> {
                    reorderedPageIds += orderedPageIds
                    val pagesById = session.pages.associateBy { page -> page.id }
                    session = session.copy(
                        pages = orderedPageIds.mapIndexed { index, pageId ->
                            pagesById.getValue(pageId).copy(pageIndex = index)
                        }
                    )
                    AppResult.Success(Unit)
                }
            }

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            error("Not used.")
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
