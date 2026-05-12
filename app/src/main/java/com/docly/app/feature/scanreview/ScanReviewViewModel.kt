package com.docly.app.feature.scanreview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
import com.docly.app.domain.usecase.scanner.SaveScannedOutputUseCase
import com.docly.app.domain.usecase.scanner.ScannedOutputFormat
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
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
class ScanReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val reorderPagesUseCase: ReorderPagesUseCase,
    private val rotatePageUseCase: RotatePageUseCase,
    private val saveScannedOutputUseCase: SaveScannedOutputUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScanReviewUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<ScanReviewUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ScanReviewUiEffect>()
    val uiEffect: SharedFlow<ScanReviewUiEffect> = _uiEffect.asSharedFlow()

    private var sessionPages: List<ScannedPage> = emptyList()

    init {
        loadSession()
    }

    fun onEvent(event: ScanReviewUiEvent) {
        when (event) {
            ScanReviewUiEvent.OnLoad -> loadSession()

            is ScanReviewUiEvent.OnTitleChanged -> _uiState.update { state ->
                state.copy(title = event.title, errorMessage = null)
            }

            is ScanReviewUiEvent.OnOutputFormatChanged -> _uiState.update { state ->
                state.copy(outputFormat = event.outputFormat, errorMessage = null)
            }

            ScanReviewUiEvent.OnAddPageClicked -> navigateToScanner()

            is ScanReviewUiEvent.OnDeletePageClicked -> deletePage(event.pageId)

            is ScanReviewUiEvent.OnRotatePageClicked -> rotatePage(event.pageId)

            is ScanReviewUiEvent.OnMovePageUp -> movePage(pageId = event.pageId, direction = MoveDirection.UP)

            is ScanReviewUiEvent.OnMovePageDown -> movePage(pageId = event.pageId, direction = MoveDirection.DOWN)

            ScanReviewUiEvent.OnSaveClicked -> save()
        }
    }

    private fun loadSession() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            _uiState.update { state ->
                state.copy(errorMessage = "Scan session not found.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, isSaving = false, errorMessage = null)
            }
            refreshSession()
        }
    }

    private suspend fun refreshSession(errorMessage: String? = null) {
        when (val result = getScanSessionUseCase(_uiState.value.sessionId)) {
            is AppResult.Error -> _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSaving = false,
                    errorMessage = result.toUserMessage()
                )
            }

            is AppResult.Success -> {
                val session = result.data
                sessionPages = session?.orderedPages.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        pages = sessionPages,
                        isLoading = false,
                        isSaving = false,
                        errorMessage = errorMessage ?: if (session == null) "Scan session not found." else null
                    )
                }
            }
        }
    }

    private fun navigateToScanner() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            _uiState.update { state -> state.copy(errorMessage = "Scan session not found.") }
            return
        }

        viewModelScope.launch {
            _uiEffect.emit(ScanReviewUiEffect.NavigateToScanner(sessionId))
        }
    }

    private fun deletePage(pageId: String) {
        if (!_uiState.value.canEditPages || pageId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isSaving = true, errorMessage = null) }
            when (val result = deletePageUseCase(pageId)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    refreshSession(errorMessage = message)
                    _uiEffect.emit(ScanReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun rotatePage(pageId: String) {
        if (!_uiState.value.canEditPages) return
        val page = sessionPages.firstOrNull { candidate -> candidate.id == pageId } ?: return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isSaving = true, errorMessage = null) }
            when (val result = rotatePageUseCase(page)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state -> state.copy(isSaving = false, errorMessage = message) }
                    _uiEffect.emit(ScanReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun movePage(pageId: String, direction: MoveDirection) {
        if (!_uiState.value.canEditPages) return

        val currentIndex = sessionPages.indexOfFirst { page -> page.id == pageId }
        val swapIndex = when (direction) {
            MoveDirection.UP -> currentIndex - 1
            MoveDirection.DOWN -> currentIndex + 1
        }
        if (currentIndex == NOT_FOUND || swapIndex !in sessionPages.indices) return

        val reorderedPageIds = sessionPages.toMutableList().apply {
            val currentPage = this[currentIndex]
            this[currentIndex] = this[swapIndex]
            this[swapIndex] = currentPage
        }.map { page -> page.id }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isSaving = true, errorMessage = null) }
            when (val result = reorderPagesUseCase(_uiState.value.sessionId, reorderedPageIds)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state -> state.copy(isSaving = false, errorMessage = message) }
                    _uiEffect.emit(ScanReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            val message = when {
                state.title.isBlank() -> "Document title is required."
                state.pages.isEmpty() -> "Add at least one scanned page before saving."
                else -> "Scan is not ready to save."
            }
            _uiState.update { currentState -> currentState.copy(errorMessage = message) }
            viewModelScope.launch {
                _uiEffect.emit(ScanReviewUiEffect.ShowToast(message))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState -> currentState.copy(isSaving = true, errorMessage = null) }
            when (
                val result = saveScannedOutputUseCase(
                    sessionId = state.sessionId,
                    title = state.title,
                    outputFormat = state.outputFormat
                )
            ) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState -> currentState.copy(isSaving = false, errorMessage = message) }
                    _uiEffect.emit(ScanReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { currentState -> currentState.copy(isSaving = false, errorMessage = null) }
                    _uiEffect.emit(
                        ScanReviewUiEffect.ShowToast(
                            result.data.documents.savedMessage(state.outputFormat)
                        )
                    )
                    _uiEffect.emit(ScanReviewUiEffect.NavigateToDocuments)
                }
            }
        }
    }

    private fun List<com.docly.app.domain.model.DoclyDocument>.savedMessage(outputFormat: ScannedOutputFormat): String =
        when (outputFormat) {
            ScannedOutputFormat.PDF -> "PDF saved."
            ScannedOutputFormat.IMAGES -> if (size == 1) "Image saved." else "$size images saved."
        }

    private val ScanSession.orderedPages: List<ScannedPage>
        get() = pages.sortedBy { page -> page.pageIndex }

    private enum class MoveDirection {
        UP,
        DOWN
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
        const val NOT_FOUND = -1
    }
}
