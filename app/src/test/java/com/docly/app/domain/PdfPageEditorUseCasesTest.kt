package com.docly.app.domain

import com.docly.app.core.image.ScanPageRenderer
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.editor.LoadScannedPdfPageEditorUseCase
import com.docly.app.domain.usecase.editor.SaveScannedPdfPageEditsUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfPageEditorUseCasesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadRejectsPdfWithoutRetainedScanSource() = runTest {
        val repository = FakeDocumentRepository(
            listOf(document(sourceScanSessionId = null))
        )
        val useCase = LoadScannedPdfPageEditorUseCase(repository, FakeScanRepository(emptyList()))

        val result = useCase("document-id")

        assertTrue(result is AppResult.Error)
        assertEquals("Only Docly-created scan PDFs can use page tools.", (result as AppResult.Error).message)
    }

    @Test
    fun saveRegeneratesSamePdfDocumentAndPersistsPagePlan() = runTest {
        val firstImage = temporaryFolder.writeFixture("raw/first.jpg", "first")
        val secondImage = temporaryFolder.writeFixture("raw/second.jpg", "second")
        val pdfFile = temporaryFolder.writeFixture("documents/source.pdf", "old pdf")
        val firstPage = page(id = "first", originalImagePath = firstImage.absolutePath)
        val secondPage = page(id = "second", originalImagePath = secondImage.absolutePath, pageIndex = 1)
        val scanRepository = FakeScanRepository(listOf(firstPage, secondPage))
        val documentRepository = FakeDocumentRepository(
            listOf(document(path = pdfFile.absolutePath, sourceScanSessionId = "session-id"))
        )
        val pdfRepository = FakePdfRepository()
        val useCase = SaveScannedPdfPageEditsUseCase(
            documentRepository = documentRepository,
            scanRepository = scanRepository,
            pdfRepository = pdfRepository,
            fileRepository = FakeFileRepository(temporaryFolder.root),
            scanPageRenderer = FakeScanPageRenderer(),
            timeProvider = FixedTimeProvider(500L)
        )

        val result = useCase(
            documentId = "document-id",
            editedPages = listOf(secondPage.copy(rotationDegrees = 90))
        )

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("first"), scanRepository.deletedPageIds)
        assertEquals(listOf("second"), scanRepository.reorderedPageIds.single())
        assertEquals(90, scanRepository.updatedPages.single().rotationDegrees)
        assertEquals("document-id", documentRepository.documents.single().id)
        assertEquals(1, documentRepository.documents.single().pageCount)
        assertEquals(500L, documentRepository.documents.single().updatedAt)
        assertEquals("new pdf", pdfFile.readText(Charsets.UTF_8))
        assertTrue(pdfRepository.pageImagePaths.single().single().contains("page_edit"))
    }

    private fun document(
        path: String = "/documents/source.pdf",
        sourceScanSessionId: String? = "session-id"
    ): DoclyDocument = DoclyDocument(
        id = "document-id",
        name = "Scan",
        type = DocumentType.PDF,
        mimeType = "application/pdf",
        fileRef = FileRef.InternalFile(path),
        source = DocumentSource.SCANNED,
        fileSize = 10L,
        pageCount = 2,
        createdAt = 1L,
        updatedAt = 1L,
        isScanned = true,
        sourceScanSessionId = sourceScanSessionId
    )

    private fun page(id: String, originalImagePath: String, pageIndex: Int = 0): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = originalImagePath,
        processedImagePath = originalImagePath,
        thumbnailPath = originalImagePath,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

    private class FakeScanRepository(pages: List<ScannedPage>) : ScanRepository {
        private var session = ScanSession(
            id = "session-id",
            createdAt = 1L,
            updatedAt = 1L,
            status = ScanSessionStatus.EXPORTED,
            scanMode = ScanMode.DOCUMENT,
            pages = pages
        )
        val deletedPageIds = mutableListOf<String>()
        val updatedPages = mutableListOf<ScannedPage>()
        val reorderedPageIds = mutableListOf<List<String>>()

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(session)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> {
            updatedPages += page
            session = session.copy(
                pages = session.pages.map { existing ->
                    if (existing.id == page.id) {
                        page
                    } else {
                        existing
                    }
                }
            )
            return AppResult.Success(Unit)
        }

        override suspend fun deletePage(pageId: String): AppResult<Unit> {
            deletedPageIds += pageId
            session = session.copy(pages = session.pages.filterNot { page -> page.id == pageId })
            return AppResult.Success(Unit)
        }

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> {
            reorderedPageIds += orderedPageIds
            val pagesById = session.pages.associateBy { page -> page.id }
            session = session.copy(
                pages = orderedPageIds.mapIndexed { index, pageId ->
                    pagesById.getValue(pageId).copy(pageIndex = index)
                }
            )
            return AppResult.Success(Unit)
        }

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            error("Not used.")
    }

    private class FakePdfRepository : PdfRepository {
        val pageImagePaths = mutableListOf<List<String>>()

        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
            this.pageImagePaths += pageImagePaths
            File(outputPdfPath).apply {
                parentFile?.mkdirs()
                writeText("new pdf", Charsets.UTF_8)
            }
            return AppResult.Success(outputPdfPath)
        }
    }

    private class FakeFileRepository(private val root: File) : FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String =
            File(root, "raw/$suffix.jpg").absolutePath

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            File(root, "processed/$suffix.jpg").absolutePath

        override fun createThumbnailPath(sessionId: String, suffix: String): String =
            File(root, "thumb/$suffix.jpg").absolutePath

        override fun createPdfPath(fileName: String): String = File(root, "$fileName.pdf").absolutePath

        override fun createTempImagePath(suffix: String): String = File(root, "temp/$suffix.jpg").absolutePath

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> {
            File(path).delete()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> {
            paths.forEach { path -> File(path).delete() }
            return AppResult.Success(Unit)
        }

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeScanPageRenderer : ScanPageRenderer {
        override suspend fun render(page: ScannedPage, outputPath: String): AppResult<String> {
            File(outputPath).apply {
                parentFile?.mkdirs()
                writeText("rendered-${page.id}", Charsets.UTF_8)
            }
            return AppResult.Success(outputPath)
        }
    }
}

private fun TemporaryFolder.writeFixture(path: String, content: String): File {
    val file = File(root, path)
    file.parentFile?.mkdirs()
    file.writeText(content, Charsets.UTF_8)
    return file
}
