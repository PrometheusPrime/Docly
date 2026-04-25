package com.docly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.page.ApplyPageCropUseCase
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
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val applyPageCropUseCase: ApplyPageCropUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ReviewUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ReviewUiEffect>()
    val uiEffect: SharedFlow<ReviewUiEffect> = _uiEffect.asSharedFlow()

    private var currentPage: ScannedPage? = null

    init {
        loadSession()
    }

    fun onEvent(event: ReviewUiEvent) {
        when (event) {
            ReviewUiEvent.OnLoad -> loadSession()

            is ReviewUiEvent.OnScanModeChanged -> _uiState.update { state ->
                state.copy(selectedScanMode = event.scanMode, errorMessage = null)
            }

            is ReviewUiEvent.OnCornersChanged -> _uiState.update { state ->
                state.copy(editableCorners = event.corners, errorMessage = null)
            }

            ReviewUiEvent.OnResetToDetectedClicked -> resetToDetectedCorners()

            ReviewUiEvent.OnResetToFullImageClicked -> resetToFullImageCorners()

            ReviewUiEvent.OnReprocessClicked -> applyCrop()

            ReviewUiEvent.OnRotateClicked,
            ReviewUiEvent.OnAcceptClicked,
            ReviewUiEvent.OnToggleOriginalClicked -> Unit

            ReviewUiEvent.OnRescanClicked -> navigateBackToScanner()
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
                state.copy(isProcessing = true, errorMessage = null)
            }

            when (val result = getScanSessionUseCase(sessionId)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> {
                    val session = result.data
                    val latestPage = session?.pages?.maxByOrNull { page -> page.pageIndex }
                    currentPage = latestPage
                    val fallbackCorners = latestPage?.let { page ->
                        fullImageCorners(imageWidth = page.width, imageHeight = page.height)
                    }
                    _uiState.update { state ->
                        state.copy(
                            currentPageId = latestPage?.id,
                            rawImagePath = latestPage?.originalImagePath.orEmpty(),
                            processedImagePath = latestPage?.processedImagePath,
                            thumbnailPath = latestPage?.thumbnailPath,
                            imageWidth = latestPage?.width ?: 0,
                            imageHeight = latestPage?.height ?: 0,
                            selectedScanMode = latestPage?.scanMode ?: session?.scanMode ?: state.selectedScanMode,
                            appliedScanMode = latestPage?.scanMode ?: session?.scanMode ?: state.appliedScanMode,
                            detectedCorners = latestPage?.corners,
                            editableCorners = latestPage?.corners ?: fallbackCorners,
                            rotationDegrees = latestPage?.rotationDegrees ?: 0,
                            isProcessing = false,
                            errorMessage = if (session == null) "Scan session not found." else null
                        )
                    }
                }
            }
        }
    }

    private fun resetToDetectedCorners() {
        _uiState.update { state ->
            state.detectedCorners?.let { corners ->
                state.copy(editableCorners = corners, errorMessage = null)
            } ?: state
        }
    }

    private fun resetToFullImageCorners() {
        _uiState.update { state ->
            val corners = fullImageCorners(imageWidth = state.imageWidth, imageHeight = state.imageHeight)
            if (corners != null) {
                state.copy(editableCorners = corners, errorMessage = null)
            } else {
                state
            }
        }
    }

    private fun applyCrop() {
        val page = currentPage
        val state = _uiState.value
        val corners = state.editableCorners
        if (page == null || corners == null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = "No page is available to crop.")
            }
            return
        }
        if (state.isProcessing) return

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isProcessing = true, errorMessage = null)
            }

            val pageForProcessing = page.copy(scanMode = state.selectedScanMode)
            when (val result = applyPageCropUseCase(page = pageForProcessing, corners = corners)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isProcessing = false, errorMessage = message)
                    }
                    _uiEffect.emit(ReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    currentPage = result.data
                    _uiState.update { currentState ->
                        currentState.copy(
                            currentPageId = result.data.id,
                            rawImagePath = result.data.originalImagePath,
                            processedImagePath = result.data.processedImagePath,
                            thumbnailPath = result.data.thumbnailPath,
                            imageWidth = result.data.width,
                            imageHeight = result.data.height,
                            selectedScanMode = result.data.scanMode,
                            appliedScanMode = result.data.scanMode,
                            detectedCorners = result.data.corners,
                            editableCorners = result.data.corners,
                            rotationDegrees = result.data.rotationDegrees,
                            isProcessing = false,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ReviewUiEffect.ShowToast("Crop applied."))
                }
            }
        }
    }

    private fun navigateBackToScanner() {
        viewModelScope.launch {
            _uiEffect.emit(ReviewUiEffect.NavigateBackToScanner(_uiState.value.sessionId))
        }
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
