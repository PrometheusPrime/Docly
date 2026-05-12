package com.docly.app.feature.pdfpageeditor

import androidx.lifecycle.SavedStateHandle
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
import com.docly.app.domain.usecase.editor.ScannedPdfPageEditorDocument
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
class PdfPageEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun pageEditsStayDraftUntilSave() = runTest {
        val saveUseCase = FakeSaveUseCase()
        val viewModel = viewModel(saveUseCase = saveUseCase)
        advanceUntilIdle()

        viewModel.onEvent(PdfPageEditorUiEvent.OnRotatePageClicked("first"))
        viewModel.onEvent(PdfPageEditorUiEvent.OnMovePageDown("first"))
        viewModel.onEvent(PdfPageEditorUiEvent.OnDeletePageClicked("second"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDirty)
        assertEquals(listOf("first"), viewModel.uiState.value.pages.map { page -> page.id })
        assertEquals(90, viewModel.uiState.value.pages.single().rotationDegrees)
        assertTrue(saveUseCase.savedPages.isEmpty())

        val effect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(PdfPageEditorUiEvent.OnSaveClicked)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals(listOf("first"), saveUseCase.savedPages.single().map { page -> page.id })
        assertEquals(PdfPageEditorUiEffect.ShowToast("PDF updated."), effect.await())
    }

    private fun viewModel(saveUseCase: FakeSaveUseCase = FakeSaveUseCase()): PdfPageEditorViewModel =
        PdfPageEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to "document-id")),
            loadScannedPdfPageEditorUseCase = FakeLoadUseCase,
            saveScannedPdfPageEditsUseCase = saveUseCase
        ).also { viewModel -> viewModel.start() }

    private object FakeLoadUseCase : LoadScannedPdfPageEditorUseCase(
        documentRepository = FakeDocumentRepository(),
        scanRepository = EmptyScanRepository
    ) {
        override suspend operator fun invoke(documentId: String): AppResult<ScannedPdfPageEditorDocument> =
            AppResult.Success(
                ScannedPdfPageEditorDocument(
                    document = document(),
                    pages = listOf(page("first"), page("second", pageIndex = 1))
                )
            )
    }

    private class FakeSaveUseCase :
        SaveScannedPdfPageEditsUseCase(
            documentRepository = FakeDocumentRepository(),
            scanRepository = EmptyScanRepository,
            pdfRepository = EmptyPdfRepository,
            fileRepository = EmptyFileRepository,
            scanPageRenderer = EmptyScanPageRenderer,
            timeProvider = FixedTimeProvider(1L)
        ) {
        val savedPages = mutableListOf<List<ScannedPage>>()

        override suspend operator fun invoke(
            documentId: String,
            editedPages: List<ScannedPage>
        ): AppResult<ScannedPdfPageEditorDocument> {
            savedPages += editedPages
            return AppResult.Success(ScannedPdfPageEditorDocument(document = document(), pages = editedPages))
        }
    }

    private object EmptyScanRepository : ScanRepository {
        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = error("Not used.")

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

    private object EmptyPdfRepository : PdfRepository {
        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> =
            error("Not used.")
    }

    private object EmptyFileRepository : FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = error("Not used.")

        override fun createProcessedImagePath(sessionId: String, suffix: String): String = error("Not used.")

        override fun createThumbnailPath(sessionId: String, suffix: String): String = error("Not used.")

        override fun createPdfPath(fileName: String): String = error("Not used.")

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = error("Not used.")

        override suspend fun deleteFile(path: String): AppResult<Unit> = error("Not used.")

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = error("Not used.")

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = error("Not used.")

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> = error("Not used.")
    }

    private object EmptyScanPageRenderer : ScanPageRenderer {
        override suspend fun render(page: ScannedPage, outputPath: String): AppResult<String> = error("Not used.")
    }

    class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}

private fun document(): DoclyDocument = DoclyDocument(
    id = "document-id",
    name = "Scan",
    type = DocumentType.PDF,
    mimeType = "application/pdf",
    fileRef = FileRef.InternalFile("/documents/scan.pdf"),
    source = DocumentSource.SCANNED,
    fileSize = 1L,
    createdAt = 1L,
    updatedAt = 1L,
    sourceScanSessionId = "session-id"
)

private fun page(id: String, pageIndex: Int = 0): ScannedPage = ScannedPage(
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
    createdAt = 1L
)
