package com.docly.app.feature.documenteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.usecase.create.CreatePdfFromTextDocumentUseCase
import com.docly.app.domain.usecase.create.LoadEditableDocumentUseCase
import com.docly.app.domain.usecase.create.SaveEditableDocumentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    private val createPdfFromTextDocumentUseCase: CreatePdfFromTextDocumentUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DocumentEditorUiState(documentId = savedStateHandle.get<String>(DOCUMENT_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<DocumentEditorUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<DocumentEditorUiEffect>()
    val uiEffect: SharedFlow<DocumentEditorUiEffect> = _uiEffect.asSharedFlow()

    private var hasStarted = false
    private var lastSavedContent: String = ""

    fun onEvent(event: DocumentEditorUiEvent) {
        when (event) {
            DocumentEditorUiEvent.OnStart -> start()
            DocumentEditorUiEvent.OnRetryClicked -> loadDocument()
            is DocumentEditorUiEvent.OnContentChanged -> updateContent(event.content)
            DocumentEditorUiEvent.OnSaveClicked -> save()
            DocumentEditorUiEvent.OnExportPdfClicked -> exportPdf()
        }
    }

    private fun start() {
        if (hasStarted) return
        hasStarted = true
        loadDocument()
    }

    private fun loadDocument() {
        val documentId = _uiState.value.documentId
        if (documentId.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    hasLoadedContent = false,
                    errorMessage = "Document not found."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isSaving = false,
                    isExportingPdf = false,
                    errorMessage = null
                )
            }
            when (val result = loadEditableDocumentUseCase(documentId)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasLoadedContent = false,
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> {
                    lastSavedContent = result.data.content
                    _uiState.update { state ->
                        state.copy(
                            title = result.data.document.name,
                            documentType = result.data.document.type,
                            content = result.data.content,
                            isLoading = false,
                            hasLoadedContent = true,
                            isDirty = false,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun updateContent(content: String) {
        _uiState.update { state ->
            if (!state.hasLoadedContent) {
                state
            } else {
                state.copy(
                    content = content,
                    isDirty = content != lastSavedContent,
                    errorMessage = null
                )
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { currentState -> currentState.copy(isSaving = true, errorMessage = null) }
            when (val result = saveEditableDocumentUseCase(state.documentId, state.content)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    lastSavedContent = state.content
                    _uiState.update { currentState ->
                        currentState.copy(
                            title = result.data.name,
                            isSaving = false,
                            isDirty = false,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(DocumentEditorUiEffect.ShowToast("Document saved."))
                }
            }
        }
    }

    private fun exportPdf() {
        val state = _uiState.value
        if (!state.canExportPdf) return
        if (state.isDirty) {
            val message = "Save changes before creating a PDF."
            _uiState.update { currentState -> currentState.copy(errorMessage = message) }
            viewModelScope.launch {
                _uiEffect.emit(DocumentEditorUiEffect.ShowToast(message))
            }
            return
        }

        viewModelScope.launch {
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

    private companion object {
        const val DOCUMENT_ID_KEY = "documentId"
    }
}
