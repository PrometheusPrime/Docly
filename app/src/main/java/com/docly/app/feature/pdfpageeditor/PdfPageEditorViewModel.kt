package com.docly.app.feature.pdfpageeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.editor.LoadScannedPdfPageEditorUseCase
import com.docly.app.domain.usecase.editor.SaveScannedPdfPageEditsUseCase
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
class PdfPageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadScannedPdfPageEditorUseCase: LoadScannedPdfPageEditorUseCase,
    private val saveScannedPdfPageEditsUseCase: SaveScannedPdfPageEditsUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PdfPageEditorUiState(documentId = savedStateHandle.get<String>(DOCUMENT_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<PdfPageEditorUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<PdfPageEditorUiEffect>()
    val uiEffect: SharedFlow<PdfPageEditorUiEffect> = _uiEffect.asSharedFlow()

    private var hasStarted = false

    fun onEvent(event: PdfPageEditorUiEvent) {
        when (event) {
            PdfPageEditorUiEvent.OnLoad -> load()
            is PdfPageEditorUiEvent.OnDeletePageClicked -> deletePage(event.pageId)
            is PdfPageEditorUiEvent.OnRotatePageClicked -> rotatePage(event.pageId)
            is PdfPageEditorUiEvent.OnMovePageUp -> movePage(pageId = event.pageId, direction = MoveDirection.UP)
            is PdfPageEditorUiEvent.OnMovePageDown -> movePage(pageId = event.pageId, direction = MoveDirection.DOWN)
            PdfPageEditorUiEvent.OnSaveClicked -> save()
        }
    }

    fun start() {
        if (hasStarted) return
        hasStarted = true
        load()
    }

    private fun load() {
        val documentId = _uiState.value.documentId
        if (documentId.isBlank()) {
            _uiState.update { state ->
                state.copy(isLoading = false, errorMessage = "Document not found.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, isSaving = false, errorMessage = null) }
            when (val result = loadScannedPdfPageEditorUseCase(documentId)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(isLoading = false, errorMessage = result.toUserMessage())
                }

                is AppResult.Success -> _uiState.update { state ->
                    state.copy(
                        title = result.data.document.name,
                        pages = result.data.pages,
                        isLoading = false,
                        isDirty = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun deletePage(pageId: String) {
        _uiState.update { state ->
            if (!state.canEditPages || state.pages.size <= 1) {
                state
            } else {
                state.copy(
                    pages = state.pages.filterNot { page -> page.id == pageId }.reindexPages(),
                    isDirty = true,
                    errorMessage = null
                )
            }
        }
    }

    private fun rotatePage(pageId: String) {
        _uiState.update { state ->
            if (!state.canEditPages) {
                state
            } else {
                state.copy(
                    pages = state.pages.map { page ->
                        if (page.id == pageId) {
                            val rotationDegrees = (page.rotationDegrees + RIGHT_ANGLE_DEGREES) % FULL_ROTATION_DEGREES
                            page.copy(rotationDegrees = rotationDegrees)
                        } else {
                            page
                        }
                    },
                    isDirty = true,
                    errorMessage = null
                )
            }
        }
    }

    private fun movePage(pageId: String, direction: MoveDirection) {
        _uiState.update { state ->
            if (!state.canEditPages) return@update state
            val currentIndex = state.pages.indexOfFirst { page -> page.id == pageId }
            val targetIndex = when (direction) {
                MoveDirection.UP -> currentIndex - 1
                MoveDirection.DOWN -> currentIndex + 1
            }
            if (currentIndex !in state.pages.indices || targetIndex !in state.pages.indices) {
                state
            } else {
                val nextPages = state.pages.toMutableList()
                val movedPage = nextPages.removeAt(currentIndex)
                nextPages.add(targetIndex, movedPage)
                state.copy(pages = nextPages.reindexPages(), isDirty = true, errorMessage = null)
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isSaving = true, errorMessage = null) }
            when (val result = saveScannedPdfPageEditsUseCase(state.documentId, state.pages)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { current -> current.copy(isSaving = false, errorMessage = message) }
                    _uiEffect.emit(PdfPageEditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            title = result.data.document.name,
                            pages = result.data.pages,
                            isSaving = false,
                            isDirty = false,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(PdfPageEditorUiEffect.ShowToast("PDF updated."))
                    _uiEffect.emit(PdfPageEditorUiEffect.NavigateToReader(result.data.document.id))
                }
            }
        }
    }

    private fun List<ScannedPage>.reindexPages(): List<ScannedPage> = mapIndexed { index, page ->
        page.copy(pageIndex = index)
    }

    private enum class MoveDirection {
        UP,
        DOWN
    }

    private companion object {
        const val DOCUMENT_ID_KEY = "documentId"
        const val RIGHT_ANGLE_DEGREES = 90
        const val FULL_ROTATION_DEGREES = 360
    }
}
