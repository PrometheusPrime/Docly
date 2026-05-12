package com.docly.app.domain

import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.image.ScanQualityIssue
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.usecase.page.EvaluateScanQualityUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateScanQualityUseCaseTest {
    @Test
    fun returnsRepositoryQualityAssessment() = runBlocking {
        val assessment = ScanQualityAssessment.good().copy(issues = setOf(ScanQualityIssue.TOO_DARK))
        val repository = FakeImageProcessingRepository(result = AppResult.Success(assessment))
        val corners = sampleCorners()

        val result = EvaluateScanQualityUseCase(repository)(inputPath = "/raw/page.jpg", corners = corners)

        assertEquals(assessment, result.getOrNull())
        assertEquals(QualityRequest(inputPath = "/raw/page.jpg", corners = corners), repository.requests.single())
    }

    @Test
    fun propagatesRepositoryError() = runBlocking {
        val result = EvaluateScanQualityUseCase(
            FakeImageProcessingRepository(
                result = AppResult.Error(
                    message = "Scan quality could not be evaluated.",
                    category = AppErrorCategory.PROCESSING
                )
            )
        )(inputPath = "/raw/page.jpg", corners = sampleCorners())

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
    }

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 30f),
        topRight = PointFSerializable(900f, 40f),
        bottomRight = PointFSerializable(880f, 1300f),
        bottomLeft = PointFSerializable(30f, 1290f)
    )

    private class FakeImageProcessingRepository(private val result: AppResult<ScanQualityAssessment>) :
        ImageProcessingRepository {
        val requests: MutableList<QualityRequest> = mutableListOf()

        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = AppResult.Success(null)

        override suspend fun evaluateQuality(
            inputPath: String,
            corners: PageCorners?
        ): AppResult<ScanQualityAssessment> {
            requests += QualityRequest(inputPath = inputPath, corners = corners)
            return result
        }

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

    private data class QualityRequest(val inputPath: String, val corners: PageCorners?)
}
