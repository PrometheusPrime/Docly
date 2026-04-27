package com.docly.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
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
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val reorderPagesUseCase: ReorderPagesUseCase,
    private val rotatePageUseCase: RotatePageUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditorUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<EditorUiEffect>()
    val uiEffect: SharedFlow<EditorUiEffect> = _uiEffect.asSharedFlow()

    private var sessionPages: List<ScannedPage> = emptyList()

    init {
        loadSession()
    }

    fun onEvent(event: EditorUiEvent) {
        when (event) {
            EditorUiEvent.OnLoad -> loadSession()
            EditorUiEvent.OnAddPageClicked -> navigateToScanner()
            is EditorUiEvent.OnDeletePageClicked -> deletePage(event.pageId)
            is EditorUiEvent.OnRotatePageClicked -> rotatePage(event.pageId)
            is EditorUiEvent.OnMovePageUp -> moveAcceptedPage(pageId = event.pageId, direction = MoveDirection.UP)
            is EditorUiEvent.OnMovePageDown -> moveAcceptedPage(pageId = event.pageId, direction = MoveDirection.DOWN)
            EditorUiEvent.OnContinueClicked -> continueToMetadata()
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
                        pages = sessionPages.acceptedPages(),
                        pendingPageCount = session?.pendingPageCount ?: 0,
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
            _uiEffect.emit(EditorUiEffect.NavigateToScanner(sessionId))
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
                    _uiEffect.emit(EditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun rotatePage(pageId: String) {
        if (!_uiState.value.canEditPages) return
        val page = sessionPages.firstOrNull { candidate -> candidate.id == pageId }
            ?: return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isSaving = true, errorMessage = null) }

            when (val result = rotatePageUseCase(page)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(EditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun moveAcceptedPage(pageId: String, direction: MoveDirection) {
        if (!_uiState.value.canEditPages) return

        val acceptedPages = sessionPages.acceptedPages()
        val acceptedIndex = acceptedPages.indexOfFirst { page -> page.id == pageId }
        val swapAcceptedIndex = when (direction) {
            MoveDirection.UP -> acceptedIndex - 1
            MoveDirection.DOWN -> acceptedIndex + 1
        }
        if (acceptedIndex == NOT_FOUND || swapAcceptedIndex !in acceptedPages.indices) return

        val currentPage = acceptedPages[acceptedIndex]
        val swapPage = acceptedPages[swapAcceptedIndex]
        val reorderedPageIds = sessionPages.map { page ->
            when (page.id) {
                currentPage.id -> swapPage.id
                swapPage.id -> currentPage.id
                else -> page.id
            }
        }

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isSaving = true, errorMessage = null) }

            when (val result = reorderPagesUseCase(_uiState.value.sessionId, reorderedPageIds)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(EditorUiEffect.ShowToast(message))
                }

                is AppResult.Success -> refreshSession()
            }
        }
    }

    private fun continueToMetadata() {
        val state = _uiState.value
        val message = when {
            state.sessionId.isBlank() -> "Scan session not found."
            state.pages.isEmpty() -> "Add at least one accepted page before continuing."
            state.pendingPageCount > 0 -> "Review all pending pages before continuing."
            !state.canEditPages -> null
            else -> null
        }

        if (message != null) {
            _uiState.update { currentState -> currentState.copy(errorMessage = message) }
            viewModelScope.launch {
                _uiEffect.emit(EditorUiEffect.ShowToast(message))
            }
            return
        }

        if (!state.canContinue) return

        viewModelScope.launch {
            _uiEffect.emit(EditorUiEffect.NavigateToMetadata(state.sessionId))
        }
    }

    private val ScanSession.orderedPages: List<ScannedPage>
        get() = pages.sortedBy { page -> page.pageIndex }

    private val ScanSession.pendingPageCount: Int
        get() = pages.count { page -> page.reviewStatus == PageReviewStatus.PENDING }

    private fun List<ScannedPage>.acceptedPages(): List<ScannedPage> =
        filter { page -> page.reviewStatus == PageReviewStatus.ACCEPTED }

    private enum class MoveDirection {
        UP,
        DOWN
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
        const val NOT_FOUND = -1
    }
}
