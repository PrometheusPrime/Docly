package com.docly.app.domain

import com.docly.app.core.reader.PdfDocumentInfo
import com.docly.app.core.reader.PdfReaderEngine
import com.docly.app.core.reader.RenderedPdfPage
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.usecase.library.GenerateDocumentThumbnailUseCase
import com.docly.app.domain.usecase.reader.RenderPdfPageUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateDocumentThumbnailUseCaseTest {
    @Test
    fun imageDocumentGeneratesDurableThumbnailAndUpdatesDocument() = kotlinx.coroutines.runBlocking {
        val repository = FakeDocumentRepository(listOf(document(type = DocumentType.IMAGE, path = "/docs/image.jpg")))
        val imageProcessingRepository = FakeImageProcessingRepository()
        val useCase = useCase(repository = repository, imageProcessingRepository = imageProcessingRepository)

        val result = useCase("doc-id")

        assertEquals(true, result is AppResult.Success)
        assertEquals(listOf("/docs/image.jpg" to "/thumb/doc-id/library.jpg"), imageProcessingRepository.requests)
        assertEquals(listOf("doc-id" to "/thumb/doc-id/library.jpg"), repository.thumbnailUpdates)
    }

    @Test
    fun pdfDocumentRendersFirstPageBeforeGeneratingThumbnail() = kotlinx.coroutines.runBlocking {
        val repository = FakeDocumentRepository(listOf(document(type = DocumentType.PDF, path = "/docs/file.pdf")))
        val pdfReaderEngine = FakePdfReaderEngine()
        val imageProcessingRepository = FakeImageProcessingRepository()
        val useCase = useCase(
            repository = repository,
            pdfReaderEngine = pdfReaderEngine,
            imageProcessingRepository = imageProcessingRepository
        )

        useCase("doc-id")

        assertEquals(listOf(0), pdfReaderEngine.renderedPages)
        assertEquals(listOf("/cache/page-0.png" to "/thumb/doc-id/library.jpg"), imageProcessingRepository.requests)
    }

    @Test
    fun unsupportedTypeIsNoOp() = kotlinx.coroutines.runBlocking {
        val repository = FakeDocumentRepository(listOf(document(type = DocumentType.TXT, path = "/docs/file.txt")))
        val imageProcessingRepository = FakeImageProcessingRepository()
        val useCase = useCase(repository = repository, imageProcessingRepository = imageProcessingRepository)

        val result = useCase("doc-id")

        assertEquals(true, result is AppResult.Success)
        assertEquals(emptyList<Pair<String, String>>(), imageProcessingRepository.requests)
        assertEquals(emptyList<Pair<String, String>>(), repository.thumbnailUpdates)
    }

    @Test
    fun thumbnailFailureDoesNotUpdateDocument() = kotlinx.coroutines.runBlocking {
        val repository = FakeDocumentRepository(listOf(document(type = DocumentType.IMAGE, path = "/docs/image.jpg")))
        val useCase = useCase(
            repository = repository,
            imageProcessingRepository = FakeImageProcessingRepository(
                result = AppResult.Error("Thumbnail failed.", AppErrorCategory.PROCESSING)
            )
        )

        val result = useCase("doc-id")

        assertEquals(true, result is AppResult.Error)
        assertEquals(emptyList<Pair<String, String>>(), repository.thumbnailUpdates)
    }

    private fun useCase(
        repository: FakeDocumentRepository,
        imageProcessingRepository: FakeImageProcessingRepository = FakeImageProcessingRepository(),
        pdfReaderEngine: PdfReaderEngine = FakePdfReaderEngine()
    ): GenerateDocumentThumbnailUseCase = GenerateDocumentThumbnailUseCase(
        documentRepository = repository,
        fileRepository = FakeFileRepository(),
        imageProcessingRepository = imageProcessingRepository,
        renderPdfPageUseCase = RenderPdfPageUseCase(pdfReaderEngine)
    )

    private fun document(type: DocumentType, path: String): DoclyDocument = DoclyDocument(
        id = "doc-id",
        name = "Document",
        type = type,
        mimeType = null,
        fileRef = FileRef.InternalFile(path),
        source = DocumentSource.IMPORTED,
        createdAt = 1L,
        updatedAt = 1L
    )

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

    private class FakeImageProcessingRepository(private val result: AppResult<String>? = null) :
        ImageProcessingRepository {
        val requests = mutableListOf<Pair<String, String>>()

        override suspend fun detectDocument(inputPath: String) = AppResult.Success(null)

        override suspend fun processPage(
            inputPath: String,
            processedOutputPath: String,
            thumbnailOutputPath: String,
            scanMode: ScanMode,
            corners: com.docly.app.domain.model.PageCorners?
        ): AppResult<ProcessedPageResult> = error("Unused")

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> {
            requests += inputPath to outputPath
            return result ?: AppResult.Success(outputPath)
        }
    }

    private class FakePdfReaderEngine : PdfReaderEngine {
        val renderedPages = mutableListOf<Int>()

        override suspend fun open(fileRef: FileRef): AppResult<PdfDocumentInfo> =
            AppResult.Success(PdfDocumentInfo(pageCount = 1))

        override suspend fun renderPage(
            documentId: String,
            fileRef: FileRef,
            pageIndex: Int,
            widthPx: Int,
            zoom: Float
        ): AppResult<RenderedPdfPage> {
            renderedPages += pageIndex
            return AppResult.Success(
                RenderedPdfPage(
                    pageIndex = pageIndex,
                    width = widthPx,
                    height = 100,
                    imagePath = "/cache/page-$pageIndex.png"
                )
            )
        }
    }
}
