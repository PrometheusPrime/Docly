package com.docly.app.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.ReaderThemeMode
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
import com.docly.app.domain.usecase.settings.ObserveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val updateLastOpenedUseCase: UpdateLastOpenedUseCase,
    private val capabilityResolver: DocumentCapabilityResolver,
    private val openPdfDocumentUseCase: OpenPdfDocumentUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val readTextChunkUseCase: ReadTextChunkUseCase,
    private val renderMarkdownUseCase: RenderMarkdownUseCase,
    private val readHtmlUseCase: ReadHtmlUseCase,
    private val parseDocxUseCase: ParseDocxUseCase,
    private val openXlsxUseCase: OpenXlsxUseCase,
    private val readXlsxRowsUseCase: ReadXlsxRowsUseCase,
    private val observeSettingsUseCase: ObserveSettingsUseCase
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>(DOCUMENT_ID_KEY).orEmpty()
    private val _uiState = MutableStateFlow(ReaderUiState(documentId = documentId))
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var hasStarted = false
    private var currentDocument: DoclyDocument? = null

    init {
        observeDefaults()
    }

    fun onEvent(event: ReaderUiEvent) {
        when (event) {
            ReaderUiEvent.OnStart -> start()

            ReaderUiEvent.OnRetryClicked -> loadDocument()

            ReaderUiEvent.OnPdfPreviousPageClicked -> navigatePdfPage(delta = -1)

            ReaderUiEvent.OnPdfNextPageClicked -> navigatePdfPage(delta = 1)

            ReaderUiEvent.OnPdfZoomInClicked -> zoomPdf(delta = PDF_ZOOM_STEP)

            ReaderUiEvent.OnPdfZoomOutClicked -> zoomPdf(delta = -PDF_ZOOM_STEP)

            is ReaderUiEvent.OnPdfTargetWidthChanged -> updatePdfTargetWidth(event.widthPx)

            ReaderUiEvent.OnLoadMoreTextClicked -> loadMoreText()

            ReaderUiEvent.OnTextSizeIncreaseClicked -> updateTextSize(TEXT_SIZE_STEP)

            ReaderUiEvent.OnTextSizeDecreaseClicked -> updateTextSize(-TEXT_SIZE_STEP)

            ReaderUiEvent.OnReaderThemeToggled -> _uiState.update { state ->
                state.copy(useDarkReaderTheme = !state.useDarkReaderTheme)
            }

            is ReaderUiEvent.OnXlsxSheetSelected -> selectXlsxSheet(event.sheetIndex)

            ReaderUiEvent.OnLoadMoreRowsClicked -> loadMoreRows()
        }
    }

    private fun start() {
        if (hasStarted) return
        hasStarted = true
        loadDocument()
    }

    private fun observeDefaults() {
        viewModelScope.launch {
            observeSettingsUseCase().collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        textSizeSp = settings.readerTextSizeSp,
                        useDarkReaderTheme = settings.readerThemeMode == ReaderThemeMode.DARK
                    )
                }
            }
        }
    }

    private fun loadDocument() {
        if (documentId.isBlank()) {
            _uiState.update { state ->
                state.copy(isLoading = false, errorMessage = "Document not found.", content = null)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, errorMessage = null, content = null) }
            when (val result = getDocumentUseCase(documentId)) {
                is AppResult.Error -> showBlockingError(result.toUserMessage())

                is AppResult.Success -> {
                    val document = result.data
                    if (document == null) {
                        showBlockingError("Document not found.")
                        return@launch
                    }
                    currentDocument = document
                    updateLastOpenedUseCase(document.id)
                    _uiState.update { state ->
                        state.copy(title = document.name, documentType = document.type)
                    }
                    loadContent(document)
                }
            }
        }
    }

    private suspend fun loadContent(document: DoclyDocument) {
        val capabilities = capabilityResolver.resolve(document.type)
        if (!capabilities.canView || document.type == DocumentType.IMAGE) {
            showBlockingError(capabilities.limitationMessage ?: "Docly cannot open this file type yet.")
            return
        }

        when (document.type) {
            DocumentType.PDF -> loadPdf(document)

            DocumentType.TXT,
            DocumentType.CSV -> loadText(document)

            DocumentType.MARKDOWN -> loadMarkdown(document)

            DocumentType.HTML -> loadHtml(document)

            DocumentType.DOCX -> loadDocx(document)

            DocumentType.XLSX -> loadXlsx(document)

            DocumentType.IMAGE,
            DocumentType.UNKNOWN -> showBlockingError(
                capabilities.limitationMessage ?: "Docly cannot open this file type yet."
            )
        }
    }

    private suspend fun loadPdf(document: DoclyDocument) {
        when (val openResult = openPdfDocumentUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(openResult.toUserMessage())

            is AppResult.Success -> {
                val content = ReaderContent.Pdf(
                    pageCount = openResult.data.pageCount,
                    currentPageIndex = 0,
                    renderedPagePath = null,
                    renderedWidth = 0,
                    renderedHeight = 0,
                    zoom = 1f,
                    targetWidthPx = DEFAULT_PDF_RENDER_WIDTH_PX
                )
                _uiState.update { state ->
                    state.copy(isLoading = false, errorMessage = null, content = content)
                }
                renderPdfPage(pageIndex = 0, zoom = content.zoom, targetWidthPx = content.targetWidthPx)
            }
        }
    }

    private suspend fun loadText(document: DoclyDocument) {
        when (val result = readTextChunkUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(result.toUserMessage())

            is AppResult.Success -> _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    content = ReaderContent.Text(
                        lines = result.data.lines,
                        nextOffset = result.data.nextOffset,
                        hasMore = result.data.hasMore
                    )
                )
            }
        }
    }

    private suspend fun loadMarkdown(document: DoclyDocument) {
        when (val result = renderMarkdownUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(result.toUserMessage())

            is AppResult.Success -> _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    content = ReaderContent.Web(html = result.data.html)
                )
            }
        }
    }

    private suspend fun loadHtml(document: DoclyDocument) {
        when (val result = readHtmlUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(result.toUserMessage())

            is AppResult.Success -> _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    content = ReaderContent.Web(html = result.data.html)
                )
            }
        }
    }

    private suspend fun loadDocx(document: DoclyDocument) {
        val message = capabilityResolver.resolve(DocumentType.DOCX).limitationMessage.orEmpty()
        when (val result = parseDocxUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(result.toUserMessage())

            is AppResult.Success -> _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = null,
                    content = ReaderContent.Docx(blocks = result.data.blocks, simplifiedMessage = message)
                )
            }
        }
    }

    private suspend fun loadXlsx(document: DoclyDocument) {
        val message = capabilityResolver.resolve(DocumentType.XLSX).limitationMessage.orEmpty()
        when (val openResult = openXlsxUseCase(document.fileRef)) {
            is AppResult.Error -> showBlockingError(openResult.toUserMessage())

            is AppResult.Success -> {
                val sheets = openResult.data.sheets
                if (sheets.isEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = null,
                            content = ReaderContent.Xlsx(
                                sheets = emptyList(),
                                selectedSheetIndex = 0,
                                rows = emptyList(),
                                nextRowIndex = null,
                                hasMore = false,
                                simplifiedMessage = message
                            )
                        )
                    }
                    return
                }

                when (
                    val rowsResult = readXlsxRowsUseCase(
                        fileRef = document.fileRef,
                        sheetIndex = sheets.first().index,
                        startRowIndex = 0
                    )
                ) {
                    is AppResult.Error -> showBlockingError(rowsResult.toUserMessage())

                    is AppResult.Success -> _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = null,
                            content = ReaderContent.Xlsx(
                                sheets = sheets,
                                selectedSheetIndex = sheets.first().index,
                                rows = rowsResult.data.rows,
                                nextRowIndex = rowsResult.data.nextRowIndex,
                                hasMore = rowsResult.data.hasMore,
                                simplifiedMessage = message
                            )
                        )
                    }
                }
            }
        }
    }

    private fun navigatePdfPage(delta: Int) {
        val document = currentDocument ?: return
        val content = _uiState.value.content as? ReaderContent.Pdf ?: return
        val nextPage = (content.currentPageIndex + delta).coerceIn(0, content.pageCount - 1)
        if (nextPage == content.currentPageIndex) return

        viewModelScope.launch {
            renderPdfPage(
                pageIndex = nextPage,
                zoom = content.zoom,
                targetWidthPx = content.targetWidthPx,
                document = document
            )
        }
    }

    private fun zoomPdf(delta: Float) {
        val content = _uiState.value.content as? ReaderContent.Pdf ?: return
        val nextZoom = (content.zoom + delta).coerceIn(MIN_PDF_ZOOM, MAX_PDF_ZOOM)
        if (nextZoom == content.zoom) return

        viewModelScope.launch {
            renderPdfPage(
                pageIndex = content.currentPageIndex,
                zoom = nextZoom,
                targetWidthPx = content.targetWidthPx
            )
        }
    }

    private fun updatePdfTargetWidth(widthPx: Int) {
        val content = _uiState.value.content as? ReaderContent.Pdf ?: return
        val safeWidth = widthPx.coerceAtLeast(MIN_PDF_RENDER_WIDTH_PX)
        if (kotlin.math.abs(safeWidth - content.targetWidthPx) < PDF_WIDTH_RERENDER_THRESHOLD_PX) return

        viewModelScope.launch {
            renderPdfPage(
                pageIndex = content.currentPageIndex,
                zoom = content.zoom,
                targetWidthPx = safeWidth
            )
        }
    }

    private suspend fun renderPdfPage(
        pageIndex: Int,
        zoom: Float,
        targetWidthPx: Int,
        document: DoclyDocument? = currentDocument
    ) {
        val pdfDocument = document ?: return
        val currentContent = _uiState.value.content as? ReaderContent.Pdf ?: return
        _uiState.update { state -> state.copy(isLoadingMore = true, errorMessage = null) }
        when (
            val result = renderPdfPageUseCase(
                documentId = pdfDocument.id,
                fileRef = pdfDocument.fileRef,
                pageIndex = pageIndex,
                widthPx = targetWidthPx,
                zoom = zoom
            )
        ) {
            is AppResult.Error -> _uiState.update { state ->
                state.copy(isLoadingMore = false, errorMessage = result.toUserMessage())
            }

            is AppResult.Success -> _uiState.update { state ->
                state.copy(
                    isLoadingMore = false,
                    errorMessage = null,
                    content = currentContent.copy(
                        currentPageIndex = pageIndex,
                        renderedPagePath = result.data.imagePath,
                        renderedWidth = result.data.width,
                        renderedHeight = result.data.height,
                        zoom = zoom,
                        targetWidthPx = targetWidthPx
                    )
                )
            }
        }
    }

    private fun loadMoreText() {
        val document = currentDocument ?: return
        val content = _uiState.value.content as? ReaderContent.Text ?: return
        val nextOffset = content.nextOffset ?: return
        if (_uiState.value.isLoadingMore) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoadingMore = true, errorMessage = null) }
            when (val result = readTextChunkUseCase(document.fileRef, nextOffset)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(isLoadingMore = false, errorMessage = result.toUserMessage())
                }

                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        errorMessage = null,
                        content = content.copy(
                            lines = content.lines + result.data.lines,
                            nextOffset = result.data.nextOffset,
                            hasMore = result.data.hasMore
                        )
                    )
                }
            }
        }
    }

    private fun selectXlsxSheet(sheetIndex: Int) {
        val document = currentDocument ?: return
        val content = _uiState.value.content as? ReaderContent.Xlsx ?: return
        if (content.selectedSheetIndex == sheetIndex || content.sheets.none { it.index == sheetIndex }) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoadingMore = true, errorMessage = null) }
            when (
                val result = readXlsxRowsUseCase(
                    fileRef = document.fileRef,
                    sheetIndex = sheetIndex,
                    startRowIndex = 0
                )
            ) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(isLoadingMore = false, errorMessage = result.toUserMessage())
                }

                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        errorMessage = null,
                        content = content.copy(
                            selectedSheetIndex = sheetIndex,
                            rows = result.data.rows,
                            nextRowIndex = result.data.nextRowIndex,
                            hasMore = result.data.hasMore
                        )
                    )
                }
            }
        }
    }

    private fun loadMoreRows() {
        val document = currentDocument ?: return
        val content = _uiState.value.content as? ReaderContent.Xlsx ?: return
        val nextRowIndex = content.nextRowIndex ?: return
        if (_uiState.value.isLoadingMore) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoadingMore = true, errorMessage = null) }
            when (
                val result = readXlsxRowsUseCase(
                    fileRef = document.fileRef,
                    sheetIndex = content.selectedSheetIndex,
                    startRowIndex = nextRowIndex
                )
            ) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(isLoadingMore = false, errorMessage = result.toUserMessage())
                }

                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        errorMessage = null,
                        content = content.copy(
                            rows = content.rows + result.data.rows,
                            nextRowIndex = result.data.nextRowIndex,
                            hasMore = result.data.hasMore
                        )
                    )
                }
            }
        }
    }

    private fun updateTextSize(delta: Float) {
        _uiState.update { state ->
            state.copy(textSizeSp = (state.textSizeSp + delta).coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP))
        }
    }

    private fun showBlockingError(message: String) {
        _uiState.update { state ->
            state.copy(isLoading = false, isLoadingMore = false, errorMessage = message, content = null)
        }
    }

    private companion object {
        const val DOCUMENT_ID_KEY = "documentId"
        const val DEFAULT_PDF_RENDER_WIDTH_PX = 1080
        const val MIN_PDF_RENDER_WIDTH_PX = 320
        const val PDF_WIDTH_RERENDER_THRESHOLD_PX = 80
        const val MIN_PDF_ZOOM = 0.75f
        const val MAX_PDF_ZOOM = 3f
        const val PDF_ZOOM_STEP = 0.25f
        const val MIN_TEXT_SIZE_SP = 12f
        const val MAX_TEXT_SIZE_SP = 28f
        const val TEXT_SIZE_STEP = 2f
    }
}
