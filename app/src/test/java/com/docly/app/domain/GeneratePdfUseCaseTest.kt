package com.docly.app.domain

import com.docly.app.core.pdf.PdfGenerationOptions
import com.docly.app.core.pdf.PdfRenderQuality
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeSettingsRepository
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.settings.GetSettingsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratePdfUseCaseTest {
    @Test
    fun defaultSettingsKeepHighPdfQuality() = runBlocking {
        val pdfRepository = FakePdfRepository()
        val useCase = GeneratePdfUseCase(
            pdfRepository = pdfRepository,
            fileRepository = FakeFileRepository(),
            getSettingsUseCase = GetSettingsUseCase(FakeSettingsRepository())
        )

        useCase("document.pdf", listOf(page()))

        assertEquals(PdfRenderQuality.High, pdfRepository.options.single().renderQuality)
    }

    @Test
    fun pdfQualitySettingChangesGeneratedOptions() = runBlocking {
        val pdfRepository = FakePdfRepository()
        val useCase = GeneratePdfUseCase(
            pdfRepository = pdfRepository,
            fileRepository = FakeFileRepository(),
            getSettingsUseCase = GetSettingsUseCase(
                FakeSettingsRepository(AppSettings(defaultPdfQuality = PdfExportQuality.MEDIUM))
            )
        )

        useCase("document.pdf", listOf(page()))

        assertEquals(PdfRenderQuality.Medium, pdfRepository.options.single().renderQuality)
    }

    private fun page(): ScannedPage = ScannedPage(
        id = "page-id",
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = "/raw/page.jpg",
        processedImagePath = "/processed/page.jpg",
        thumbnailPath = "/thumb/page.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

    private class FakePdfRepository : PdfRepository {
        val options = mutableListOf<PdfGenerationOptions>()

        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> =
            AppResult.Success(outputPdfPath)

        override suspend fun createPdf(
            pageImagePaths: List<String>,
            outputPdfPath: String,
            options: PdfGenerationOptions
        ): AppResult<String> {
            this.options += options
            return AppResult.Success(outputPdfPath)
        }
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
}
