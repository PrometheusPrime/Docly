package com.docly.app.domain

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.page.AcceptReviewedPageUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptReviewedPageUseCaseTest {
    @Test
    fun acceptsPendingProcessedPage() = runBlocking {
        val repository = FakeScanRepository()
        val page = samplePage(
            processedImagePath = "/processed/page.jpg",
            reviewStatus = PageReviewStatus.PENDING
        )

        val result = AcceptReviewedPageUseCase(repository)(page)

        assertEquals(PageReviewStatus.ACCEPTED, result.getOrNull()?.reviewStatus)
        assertEquals(PageReviewStatus.ACCEPTED, repository.updatedPage?.reviewStatus)
        assertEquals(page.id, repository.updatedPage?.id)
    }

    @Test
    fun rejectsPendingPageWithoutProcessedImage() = runBlocking {
        val repository = FakeScanRepository()
        val page = samplePage(
            processedImagePath = null,
            reviewStatus = PageReviewStatus.PENDING
        )

        val result = AcceptReviewedPageUseCase(repository)(page)

        assertEquals(AppErrorCategory.VALIDATION, result.errorOrNull()?.category)
        assertTrue(repository.updateCalls == 0)
    }

    @Test
    fun acceptedPageIsNoOp() = runBlocking {
        val repository = FakeScanRepository()
        val page = samplePage(
            processedImagePath = "/processed/page.jpg",
            reviewStatus = PageReviewStatus.ACCEPTED
        )

        val result = AcceptReviewedPageUseCase(repository)(page)

        assertEquals(page, result.getOrNull())
        assertTrue(repository.updateCalls == 0)
    }

    @Test
    fun repositoryFailureIsReturned() = runBlocking {
        val repository = FakeScanRepository(
            updateResult = AppResult.Error(
                message = "Could not update page.",
                category = AppErrorCategory.STORAGE
            )
        )
        val page = samplePage(
            processedImagePath = "/processed/page.jpg",
            reviewStatus = PageReviewStatus.PENDING
        )

        val result = AcceptReviewedPageUseCase(repository)(page)

        assertEquals(AppErrorCategory.STORAGE, result.errorOrNull()?.category)
        assertEquals(1, repository.updateCalls)
    }

    private fun samplePage(processedImagePath: String?, reviewStatus: PageReviewStatus): ScannedPage = ScannedPage(
        id = "page-id",
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = "/raw/page.jpg",
        processedImagePath = processedImagePath,
        thumbnailPath = "/thumb/page.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private class FakeScanRepository(private val updateResult: AppResult<Unit> = AppResult.Success(Unit)) :
        ScanRepository {
        var updatedPage: ScannedPage? = null
        var updateCalls = 0

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = error("Not used.")

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> {
            updateCalls += 1
            return when (updateResult) {
                is AppResult.Error -> updateResult

                is AppResult.Success -> {
                    updatedPage = page
                    AppResult.Success(Unit)
                }
            }
        }

        override suspend fun deletePage(pageId: String): AppResult<Unit> = error("Not used.")

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            error("Not used.")

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            error("Not used.")
    }
}
