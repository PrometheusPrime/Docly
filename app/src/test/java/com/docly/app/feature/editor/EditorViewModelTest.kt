package com.docly.app.feature.editor

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadSessionShowsAcceptedPagesOnlyInPageOrder() = runTest {
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
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    private fun viewModel(scanRepository: FakeScanRepository): EditorViewModel = EditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(SESSION_ID_KEY to "session-id")),
        getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
        deletePageUseCase = DeletePageUseCase(scanRepository),
        reorderPagesUseCase = ReorderPagesUseCase(scanRepository),
        rotatePageUseCase = RotatePageUseCase(scanRepository)
    )

    private fun samplePage(
        id: String,
        pageIndex: Int,
        reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = "/processed/$id.jpg",
        thumbnailPath = "/thumb/$id.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private class FakeScanRepository(pages: List<ScannedPage>) : ScanRepository {
        private val session = ScanSession(
            id = "session-id",
            createdAt = 1L,
            updatedAt = 1L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = ScanMode.DOCUMENT,
            pages = pages
        )

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(session)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun deletePage(pageId: String): AppResult<Unit> = error("Not used.")

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            error("Not used.")

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
