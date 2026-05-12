package com.docly.app.feature.documenteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.usecase.create.CreatePdfFromTextDocumentUseCase
import com.docly.app.domain.usecase.create.LoadEditableDocumentUseCase
import com.docly.app.domain.usecase.create.RenderEditablePreviewUseCase
import com.docly.app.domain.usecase.create.SaveEditableDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadEditableDocumentUseCase: LoadEditableDocumentUseCase,
    private val saveEditableDocumentUseCase: SaveEditableDocumentUseCase,
    private val createPdfFromTextDocumentUseCase: CreatePdfFromTextDocumentUseCase,
    private val renderEditablePreviewUseCase: RenderEditablePreviewUseCase
) : ViewModel() {
    private val autoSaveController = AutoSaveController()
    private var previewJob: Job? = null
    private var hasStarted = false
    private var lastSavedContent: String = ""
    private var lastSavedUpdatedAt: Long? = null

    private val _uiState = MutableStateFlow(
        DocumentEditorUiState(documentId = savedStateHandle.get<String>(DOCUMENT_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<DocumentEditorUiEffect>()
    val uiEffect: SharedFlow<DocumentEditorUiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: DocumentEditorUiEvent) {
        when (event) {
            DocumentEditorUiEvent.OnStart -> start()
            DocumentEditorUiEvent.OnRetryClicked -> loadDocument()
            is DocumentEditorUiEvent.OnContentChanged -> updateContent(event.content)
            is DocumentEditorUiEvent.OnEditorModeChanged -> updateEditorMode(event.mode)
            is DocumentEditorUiEvent.OnSearchQueryChanged -> updateSearchQuery(event.query)
            DocumentEditorUiEvent.OnPreviousSearchResultClicked -> moveSearchResult(direction = -1)
            DocumentEditorUiEvent.OnNextSearchResultClicked -> moveSearchResult(direction = 1)
            DocumentEditorUiEvent.OnSaveClicked -> manualSave()
            DocumentEditorUiEvent.OnExportPdfClicked -> exportPdf()
            DocumentEditorUiEvent.OnNavigateBackClicked -> navigateBackSafely()
            DocumentEditorUiEvent.OnDiscardChangesConfirmed -> discardChangesAndNavigateBack()
            DocumentEditorUiEvent.OnUnsavedChangesDismissed -> dismissUnsavedChanges()
        }
    }

    override fun onCleared() {
        autoSaveController.cancel()
        previewJob?.cancel()
        super.onCleared()
    }

    private fun start() {
        if (hasStarted) return
        hasStarted = true
        loadDocument()
    }

    private fun loadDocument() {
        autoSaveController.cancel()
        previewJob?.cancel()
        val documentId = _uiState.value.documentId
        if (documentId.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    hasLoadedContent = false,
                    errorMessage = "Document not found.",
                    saveStatus = DocumentEditorSaveStatus.ERROR
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isSaving = false,
                    isAutosaving = false,
                    isExportingPdf = false,
                    errorMessage = null
                )
            }
            when (val result = loadEditableDocumentUseCase(documentId)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasLoadedContent = false,
                        errorMessage = result.toUserMessage(),
                        saveStatus = DocumentEditorSaveStatus.ERROR
                    )
                }

                is AppResult.Success -> {
                    lastSavedContent = result.data.content
                    lastSavedUpdatedAt = result.data.document.updatedAt
                    _uiState.update { state ->
                        state.copy(
                            title = result.data.document.name,
                            documentType = result.data.document.type,
                            content = result.data.content,
                            previewHtml = "",
                            editorMode = DocumentEditorMode.SOURCE,
                            isLoading = false,
                            hasLoadedContent = true,
                            isDirty = false,
                            saveStatus = DocumentEditorSaveStatus.SAVED,
                            lastSavedAt = result.data.document.updatedAt,
                            searchQuery = "",
                            searchResultCount = 0,
                            currentSearchResultIndex = 0,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun updateContent(content: String) {
        val nextSearch = calculateSearchState(content = content, query = _uiState.value.searchQuery, currentIndex = 0)
        _uiState.update { state ->
            if (!state.hasLoadedContent) {
                state
            } else {
                state.copy(
                    content = content,
                    isDirty = content != lastSavedContent,
                    saveStatus = if (content == lastSavedContent) {
                        DocumentEditorSaveStatus.SAVED
                    } else {
                        DocumentEditorSaveStatus.UNSAVED
                    },
                    searchResultCount = nextSearch.count,
                    currentSearchResultIndex = nextSearch.index,
                    errorMessage = null
                )
            }
        }

        val state = _uiState.value
        if (state.isDirty) {
            autoSaveController.schedule(viewModelScope) {
                saveCurrentContent(showToast = false, saveStatus = DocumentEditorSaveStatus.AUTOSAVING)
            }
        } else {
            autoSaveController.cancel()
        }
        if (state.editorMode == DocumentEditorMode.PREVIEW) {
            renderPreview()
        }
    }

    private fun updateEditorMode(mode: DocumentEditorMode) {
        val state = _uiState.value
        if (mode == DocumentEditorMode.PREVIEW && !state.canPreview) return
        _uiState.update { current -> current.copy(editorMode = mode, errorMessage = null) }
        if (mode == DocumentEditorMode.PREVIEW) {
            renderPreview()
        }
    }

    private fun updateSearchQuery(query: String) {
        val state = _uiState.value
        val search = calculateSearchState(content = state.content, query = query, currentIndex = 0)
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                searchResultCount = search.count,
                currentSearchResultIndex = search.index
            )
        }
    }

    private fun moveSearchResult(direction: Int) {
        _uiState.update { state ->
            if (state.searchResultCount == 0) {
                state
            } else {
                val nextIndex = (state.currentSearchResultIndex + direction).floorMod(state.searchResultCount)
                state.copy(currentSearchResultIndex = nextIndex)
            }
        }
    }

    private fun manualSave() {
        autoSaveController.cancel()
        viewModelScope.launch {
            saveCurrentContent(showToast = true, saveStatus = DocumentEditorSaveStatus.SAVING)
        }
    }

    private suspend fun saveCurrentContent(showToast: Boolean, saveStatus: DocumentEditorSaveStatus): Boolean {
        val state = _uiState.value
        if (!state.hasLoadedContent || !state.isDirty) return true

        _uiState.update { currentState ->
            currentState.copy(
                isSaving = saveStatus == DocumentEditorSaveStatus.SAVING,
                isAutosaving = saveStatus == DocumentEditorSaveStatus.AUTOSAVING,
                saveStatus = saveStatus,
                errorMessage = null
            )
        }
        val contentToSave = state.content
        return when (
            val result = saveEditableDocumentUseCase(
                documentId = state.documentId,
                content = contentToSave,
                expectedUpdatedAt = lastSavedUpdatedAt
            )
        ) {
            is AppResult.Error -> {
                val message = result.toUserMessage()
                _uiState.update { currentState ->
                    currentState.copy(
                        isSaving = false,
                        isAutosaving = false,
                        saveStatus = DocumentEditorSaveStatus.ERROR,
                        errorMessage = message
                    )
                }
                if (showToast) {
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast(message))
                }
                false
            }

            is AppResult.Success -> {
                lastSavedContent = contentToSave
                lastSavedUpdatedAt = result.data.updatedAt
                _uiState.update { currentState ->
                    val stillDirty = currentState.content != contentToSave
                    currentState.copy(
                        title = result.data.name,
                        isSaving = false,
                        isAutosaving = false,
                        isDirty = stillDirty,
                        saveStatus = if (stillDirty) {
                            DocumentEditorSaveStatus.UNSAVED
                        } else {
                            DocumentEditorSaveStatus.SAVED
                        },
                        lastSavedAt = result.data.updatedAt,
                        errorMessage = null
                    )
                }
                if (showToast) {
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast("Document saved."))
                }
                true
            }
        }
    }

    private fun exportPdf() {
        val state = _uiState.value
        if (!state.canExportPdf) return

        autoSaveController.cancel()
        viewModelScope.launch {
            val saved = saveCurrentContent(showToast = false, saveStatus = DocumentEditorSaveStatus.SAVING)
            if (!saved) return@launch

            _uiState.update { currentState -> currentState.copy(isExportingPdf = true, errorMessage = null) }
            when (val result = createPdfFromTextDocumentUseCase(state.documentId)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isExportingPdf = false, errorMessage = message)
                    }
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(isExportingPdf = false, errorMessage = null)
                    }
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast("PDF created."))
                    _uiEffect.emit(DocumentEditorUiEffect.NavigateToReader(result.data.id))
                }
            }
        }
    }

    private fun renderPreview() {
        val state = _uiState.value
        val type = state.documentType ?: return
        if (type !in PREVIEW_DOCUMENT_TYPES) return

        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { currentState -> currentState.copy(isPreviewLoading = true, errorMessage = null) }
            when (
                val result = renderEditablePreviewUseCase(
                    type = type,
                    content = state.content,
                    title = state.title
                )
            ) {
                is AppResult.Error -> _uiState.update { currentState ->
                    currentState.copy(
                        isPreviewLoading = false,
                        saveStatus = if (currentState.isDirty) {
                            currentState.saveStatus
                        } else {
                            DocumentEditorSaveStatus.SAVED
                        },
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> _uiState.update { currentState ->
                    currentState.copy(
                        previewHtml = result.data.html,
                        isPreviewLoading = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun navigateBackSafely() {
        val state = _uiState.value
        if (state.isDirty || state.isSaving || state.isAutosaving) {
            _uiState.update { currentState -> currentState.copy(showUnsavedChangesDialog = true) }
            return
        }
        viewModelScope.launch {
            _uiEffect.emit(DocumentEditorUiEffect.NavigateBack)
        }
    }

    private fun discardChangesAndNavigateBack() {
        autoSaveController.cancel()
        viewModelScope.launch {
            _uiState.update { state -> state.copy(showUnsavedChangesDialog = false) }
            _uiEffect.emit(DocumentEditorUiEffect.NavigateBack)
        }
    }

    private fun dismissUnsavedChanges() {
        _uiState.update { state -> state.copy(showUnsavedChangesDialog = false) }
    }

    private fun calculateSearchState(content: String, query: String, currentIndex: Int): SearchState {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return SearchState(count = 0, index = 0)
        val count = Regex.escape(normalizedQuery).toRegex(RegexOption.IGNORE_CASE).findAll(content).count()
        val index = if (count == 0) 0 else currentIndex.coerceIn(0, count - 1)
        return SearchState(count = count, index = index)
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private data class SearchState(val count: Int, val index: Int)

    private companion object {
        const val DOCUMENT_ID_KEY = "documentId"
        val PREVIEW_DOCUMENT_TYPES = setOf(DocumentType.MARKDOWN, DocumentType.HTML)
    }
}
