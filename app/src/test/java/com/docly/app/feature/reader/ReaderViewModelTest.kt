package com.docly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.reader.DocxReaderEngine
import com.docly.app.core.reader.ExtractedDocument
import com.docly.app.core.reader.HtmlReaderEngine
import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.PdfDocumentInfo
import com.docly.app.core.reader.PdfReaderEngine
import com.docly.app.core.reader.RenderedHtmlDocument
import com.docly.app.core.reader.RenderedPdfPage
import com.docly.app.core.reader.TextReadChunk
import com.docly.app.core.reader.TextReaderEngine
import com.docly.app.core.reader.WorkbookDocument
import com.docly.app.core.reader.XlsxReaderEngine
import com.docly.app.core.reader.XlsxRowPage
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.core.result.AppResult
import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.usecase.library.GetDocumentUseCase
import com.docly.app.domain.usecase.library.UpdateLastOpenedUseCase
import com.docly.app.domain.usecase.reader.OpenPdfDocumentUseCase
import com.docly.app.domain.usecase.reader.OpenXlsxUseCase
import com.docly.app.domain.usecase.reader.ParseDocxUseCase
import com.docly.app.domain.usecase.reader.ReadHtmlUseCase
import com.docly.app.domain.usecase.reader.ReadTextChunkUseCase
import com.docly.app.domain.usecase.reader.ReadXlsxRowsUseCase
import com.docly.app.domain.usecase.reader.RenderMarkdownUseCase
import com.docly.app.domain.usecase.reader.RenderPdfPageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun txtDocumentLoadsChunksAndAppendsMore() = runTest {
        val repository = FakeDocumentRepository(document(type = DocumentType.TXT))
        val textEngine = FakeTextReaderEngine(
            chunks = mapOf(
                0L to TextReadChunk(lines = listOf("first"), nextOffset = 5L, hasMore = true),
                5L to TextReadChunk(lines = listOf("second"), nextOffset = null, hasMore = false)
            )
        )
        val viewModel = viewModel(repository = repository, textReaderEngine = textEngine)

        viewModel.onEvent(ReaderUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals(listOf("doc-id"), repository.lastOpenedIds)
        assertEquals(listOf("first"), (viewModel.uiState.value.content as ReaderContent.Text).lines)

        viewModel.onEvent(ReaderUiEvent.OnLoadMoreTextClicked)
        advanceUntilIdle()

        val content = viewModel.uiState.value.content as ReaderContent.Text
        assertEquals(listOf("first", "second"), content.lines)
        assertEquals(listOf(0L, 5L), textEngine.requestedOffsets)
    }

    @Test
    fun missingDocumentShowsBlockingError() = runTest {
        val viewModel = viewModel(repository = FakeDocumentRepository(null))

        viewModel.onEvent(ReaderUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals("Document not found.", viewModel.uiState.value.errorMessage)
        assertEquals(null, viewModel.uiState.value.content)
    }

    @Test
    fun pdfNavigationAndZoomRenderRequestedPages() = runTest {
        val pdfEngine = FakePdfReaderEngine(pageCount = 2)
        val viewModel = viewModel(
            repository = FakeDocumentRepository(document(type = DocumentType.PDF)),
            pdfReaderEngine = pdfEngine
        )

        viewModel.onEvent(ReaderUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ReaderUiEvent.OnPdfNextPageClicked)
        advanceUntilIdle()
        viewModel.onEvent(ReaderUiEvent.OnPdfZoomInClicked)
        advanceUntilIdle()

        val content = viewModel.uiState.value.content as ReaderContent.Pdf
        assertEquals(1, content.currentPageIndex)
        assertEquals(1.25f, content.zoom)
        assertEquals(listOf(0 to 1f, 1 to 1f, 1 to 1.25f), pdfEngine.renderRequests)
    }

    @Test
    fun xlsxSheetSelectionLoadsRowsForSelectedSheet() = runTest {
        val xlsxEngine = FakeXlsxReaderEngine()
        val viewModel = viewModel(
            repository = FakeDocumentRepository(document(type = DocumentType.XLSX)),
            xlsxReaderEngine = xlsxEngine
        )

        viewModel.onEvent(ReaderUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ReaderUiEvent.OnXlsxSheetSelected(1))
        advanceUntilIdle()

        val content = viewModel.uiState.value.content as ReaderContent.Xlsx
        assertEquals(1, content.selectedSheetIndex)
        assertEquals(listOf(listOf("sheet-1")), content.rows)
        assertEquals(listOf(0 to 0, 1 to 0), xlsxEngine.rowRequests)
    }

    private fun viewModel(
        repository: FakeDocumentRepository = FakeDocumentRepository(document(type = DocumentType.TXT)),
        pdfReaderEngine: PdfReaderEngine = FakePdfReaderEngine(),
        textReaderEngine: TextReaderEngine = FakeTextReaderEngine(),
        markdownReaderEngine: MarkdownReaderEngine = FakeMarkdownReaderEngine(),
        htmlReaderEngine: HtmlReaderEngine = FakeHtmlReaderEngine(),
        docxReaderEngine: DocxReaderEngine = FakeDocxReaderEngine(),
        xlsxReaderEngine: XlsxReaderEngine = FakeXlsxReaderEngine()
    ): ReaderViewModel = ReaderViewModel(
        savedStateHandle = SavedStateHandle(mapOf("documentId" to "doc-id")),
        getDocumentUseCase = GetDocumentUseCase(repository),
        updateLastOpenedUseCase = UpdateLastOpenedUseCase(repository),
        capabilityResolver = DocumentCapabilityResolver(),
        openPdfDocumentUseCase = OpenPdfDocumentUseCase(pdfReaderEngine),
        renderPdfPageUseCase = RenderPdfPageUseCase(pdfReaderEngine),
        readTextChunkUseCase = ReadTextChunkUseCase(textReaderEngine),
        renderMarkdownUseCase = RenderMarkdownUseCase(markdownReaderEngine),
        readHtmlUseCase = ReadHtmlUseCase(htmlReaderEngine),
        parseDocxUseCase = ParseDocxUseCase(docxReaderEngine),
        openXlsxUseCase = OpenXlsxUseCase(xlsxReaderEngine),
        readXlsxRowsUseCase = ReadXlsxRowsUseCase(xlsxReaderEngine)
    )

    private fun document(type: DocumentType): DoclyDocument = DoclyDocument(
        id = "doc-id",
        name = "Document",
        type = type,
        mimeType = null,
        fileRef = FileRef.InternalFile("/docs/document"),
        source = DocumentSource.IMPORTED,
        fileSize = 10L,
        createdAt = 1L,
        updatedAt = 1L
    )

    private class FakeDocumentRepository(document: DoclyDocument?) : DocumentRepository {
        private val documents = MutableStateFlow(document?.let(::listOf).orEmpty())
        val lastOpenedIds = mutableListOf<String>()

        override fun observeDocuments(): Flow<List<DoclyDocument>> = documents

        override suspend fun getDocument(documentId: String): AppResult<DoclyDocument?> =
            AppResult.Success(documents.value.firstOrNull { document -> document.id == documentId })

        override suspend fun importDocument(uriString: String): AppResult<DoclyDocument> = error("Unused")

        override suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit> = error("Unused")

        override suspend fun renameDocument(documentId: String, name: String): AppResult<Unit> = error("Unused")

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> = error("Unused")

        override suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit> = error("Unused")

        override suspend fun updateLastOpened(documentId: String): AppResult<Unit> {
            lastOpenedIds += documentId
            return AppResult.Success(Unit)
        }
    }

    private class FakePdfReaderEngine(private val pageCount: Int = 1) : PdfReaderEngine {
        val renderRequests = mutableListOf<Pair<Int, Float>>()

        override suspend fun open(fileRef: FileRef): AppResult<PdfDocumentInfo> =
            AppResult.Success(PdfDocumentInfo(pageCount = pageCount))

        override suspend fun renderPage(
            documentId: String,
            fileRef: FileRef,
            pageIndex: Int,
            widthPx: Int,
            zoom: Float
        ): AppResult<RenderedPdfPage> {
            renderRequests += pageIndex to zoom
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

    private class FakeTextReaderEngine(
        private val chunks: Map<Long, TextReadChunk> = mapOf(
            0L to TextReadChunk(lines = listOf("text"), nextOffset = null, hasMore = false)
        )
    ) : TextReaderEngine {
        val requestedOffsets = mutableListOf<Long>()

        override suspend fun readChunk(fileRef: FileRef, offset: Long, maxChars: Int): AppResult<TextReadChunk> {
            requestedOffsets += offset
            return AppResult.Success(checkNotNull(chunks[offset]))
        }
    }

    private class FakeMarkdownReaderEngine : MarkdownReaderEngine {
        override suspend fun render(fileRef: FileRef): AppResult<RenderedHtmlDocument> =
            AppResult.Success(RenderedHtmlDocument("<p>markdown</p>"))
    }

    private class FakeHtmlReaderEngine : HtmlReaderEngine {
        override suspend fun read(fileRef: FileRef): AppResult<RenderedHtmlDocument> =
            AppResult.Success(RenderedHtmlDocument("<p>html</p>"))
    }

    private class FakeDocxReaderEngine : DocxReaderEngine {
        override suspend fun parse(fileRef: FileRef): AppResult<ExtractedDocument> =
            AppResult.Success(ExtractedDocument(emptyList()))
    }

    private class FakeXlsxReaderEngine : XlsxReaderEngine {
        val rowRequests = mutableListOf<Pair<Int, Int>>()

        override suspend fun open(fileRef: FileRef): AppResult<WorkbookDocument> = AppResult.Success(
            WorkbookDocument(
                sheets = listOf(
                    XlsxSheetInfo(name = "First", index = 0),
                    XlsxSheetInfo(name = "Second", index = 1)
                )
            )
        )

        override suspend fun readRows(
            fileRef: FileRef,
            sheetIndex: Int,
            startRowIndex: Int,
            maxRows: Int
        ): AppResult<XlsxRowPage> {
            rowRequests += sheetIndex to startRowIndex
            return AppResult.Success(
                XlsxRowPage(
                    rows = listOf(listOf("sheet-$sheetIndex")),
                    nextRowIndex = null,
                    hasMore = false
                )
            )
        }
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
