package com.docly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.page.AcceptReviewedPageUseCase
import com.docly.app.domain.usecase.page.ApplyPageCropUseCase
import com.docly.app.domain.usecase.page.DeletePageUseCase
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
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val applyPageCropUseCase: ApplyPageCropUseCase,
    private val acceptReviewedPageUseCase: AcceptReviewedPageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val rotatePageUseCase: RotatePageUseCase
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

            ReviewUiEvent.OnToggleCropEditorClicked -> toggleCropEditor()

            ReviewUiEvent.OnRotateClicked -> rotatePage()

            ReviewUiEvent.OnAcceptClicked -> acceptPage()

            ReviewUiEvent.OnToggleOriginalClicked -> toggleOriginalPreview()

            ReviewUiEvent.OnRescanClicked -> discardForRescan()
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
                        isSaving = false,
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> {
                    val session = result.data
                    if (session == null) {
                        currentPage = null
                        _uiState.update { state ->
                            state.copy(
                                currentPageId = null,
                                rawImagePath = "",
                                processedImagePath = null,
                                thumbnailPath = null,
                                imageWidth = 0,
                                imageHeight = 0,
                                detectedCorners = null,
                                appliedCorners = null,
                                editableCorners = null,
                                isProcessing = false,
                                isSaving = false,
                                showOriginal = false,
                                isCropAdjustmentVisible = false,
                                reviewStatus = null,
                                pendingPageCount = 0,
                                acceptedPageCount = 0,
                                errorMessage = "Scan session not found."
                            )
                        }
                        return@launch
                    }

                    val selectedPage = session.firstPendingPage() ?: session.latestPage()
                    setPageState(
                        session = session,
                        page = selectedPage,
                        errorMessage = if (selectedPage == null) "Page preview pending." else null
                    )
                    if (selectedPage?.needsReviewProcessing == true) {
                        processCurrentPage(successMessage = null)
                    }
                }
            }
        }
    }

    private fun resetToDetectedCorners() {
        _uiState.update { state ->
            state.detectedCorners?.let { corners ->
                state.copy(
                    editableCorners = corners,
                    isCropAdjustmentVisible = true,
                    showOriginal = false,
                    errorMessage = null
                )
            } ?: state
        }
    }

    private fun resetToFullImageCorners() {
        _uiState.update { state ->
            val corners = fullImageCorners(imageWidth = state.imageWidth, imageHeight = state.imageHeight)
            if (corners != null) {
                state.copy(
                    editableCorners = corners,
                    isCropAdjustmentVisible = true,
                    showOriginal = false,
                    errorMessage = null
                )
            } else {
                state
            }
        }
    }

    private fun applyCrop() {
        viewModelScope.launch {
            processCurrentPage(successMessage = "Crop applied.")
        }
    }

    private suspend fun processCurrentPage(successMessage: String?) {
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
                val appliedCorners = result.data.corners ?: corners
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
                        detectedCorners = currentState.detectedCorners ?: result.data.corners,
                        appliedCorners = appliedCorners,
                        editableCorners = appliedCorners,
                        rotationDegrees = result.data.rotationDegrees,
                        reviewStatus = result.data.reviewStatus,
                        isProcessing = false,
                        showOriginal = false,
                        isCropAdjustmentVisible = false,
                        errorMessage = null
                    )
                }
                if (successMessage != null) {
                    _uiEffect.emit(ReviewUiEffect.ShowToast(successMessage))
                }
            }
        }
    }

    private fun toggleCropEditor() {
        _uiState.update { state ->
            val corners = state.editableCorners
                ?: fullImageCorners(imageWidth = state.imageWidth, imageHeight = state.imageHeight)
            if (!state.canAdjustCrop || corners == null) {
                state
            } else {
                state.copy(
                    editableCorners = corners,
                    isCropAdjustmentVisible = !state.isCropAdjustmentVisible,
                    showOriginal = false,
                    errorMessage = null
                )
            }
        }
    }

    private fun toggleOriginalPreview() {
        _uiState.update { state ->
            if (state.canToggleOriginal) {
                state.copy(
                    showOriginal = !state.showOriginal,
                    isCropAdjustmentVisible = false,
                    errorMessage = null
                )
            } else {
                state
            }
        }
    }

    private fun rotatePage() {
        val page = currentPage ?: return
        if (!_uiState.value.canRotate) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isSaving = true, errorMessage = null)
            }

            val rotatedPage = page.copy(rotationDegrees = page.rotationDegrees.nextRightAngleRotation())
            when (val result = rotatePageUseCase(page)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(ReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    currentPage = rotatedPage
                    _uiState.update { state ->
                        state.copy(
                            rotationDegrees = rotatedPage.rotationDegrees,
                            isSaving = false,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun acceptPage() {
        val page = currentPage
        val state = _uiState.value
        if (page == null) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = "No page is available to accept.")
            }
            return
        }
        if (!state.canAccept) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = acceptBlockedMessage(currentState))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isSaving = true, errorMessage = null)
            }

            when (val result = acceptReviewedPageUseCase(page)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(ReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    currentPage = result.data
                    loadNextPendingPageOr {
                        _uiEffect.emit(ReviewUiEffect.NavigateToEditor(_uiState.value.sessionId))
                    }
                }
            }
        }
    }

    private fun discardForRescan() {
        val page = currentPage
        if (page == null) {
            navigateBackToScanner()
            return
        }
        if (!_uiState.value.canDiscardForRescan) return

        if (page.reviewStatus != PageReviewStatus.PENDING) {
            navigateBackToScanner()
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isSaving = true, errorMessage = null)
            }

            when (val result = deletePageUseCase(page.id)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(ReviewUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    currentPage = null
                    loadNextPendingPageOr {
                        _uiEffect.emit(ReviewUiEffect.NavigateBackToScanner(_uiState.value.sessionId))
                    }
                }
            }
        }
    }

    private fun navigateBackToScanner() {
        viewModelScope.launch {
            _uiEffect.emit(ReviewUiEffect.NavigateBackToScanner(_uiState.value.sessionId))
        }
    }

    private suspend fun loadNextPendingPageOr(onNoPendingPage: suspend () -> Unit) {
        when (val result = getScanSessionUseCase(_uiState.value.sessionId)) {
            is AppResult.Error -> {
                val message = result.toUserMessage()
                _uiState.update { state ->
                    state.copy(isProcessing = false, isSaving = false, errorMessage = message)
                }
                _uiEffect.emit(ReviewUiEffect.ShowToast(message))
            }

            is AppResult.Success -> {
                val session = result.data
                val nextPendingPage = session?.firstPendingPage()
                if (session == null || nextPendingPage == null) {
                    currentPage = null
                    _uiState.update { state ->
                        state.copy(
                            currentPageId = null,
                            rawImagePath = "",
                            processedImagePath = null,
                            thumbnailPath = null,
                            imageWidth = 0,
                            imageHeight = 0,
                            detectedCorners = null,
                            appliedCorners = null,
                            editableCorners = null,
                            isProcessing = false,
                            isSaving = false,
                            showOriginal = false,
                            isCropAdjustmentVisible = false,
                            reviewStatus = null,
                            pendingPageCount = session?.pendingPageCount ?: 0,
                            acceptedPageCount = session?.acceptedPageCount ?: 0,
                            errorMessage = null
                        )
                    }
                    onNoPendingPage()
                } else {
                    setPageState(session = session, page = nextPendingPage)
                    if (nextPendingPage.needsReviewProcessing) {
                        processCurrentPage(successMessage = null)
                    }
                }
            }
        }
    }

    private fun setPageState(session: ScanSession, page: ScannedPage?, errorMessage: String? = null) {
        currentPage = page
        val fallbackCorners = page?.let { currentPage ->
            fullImageCorners(imageWidth = currentPage.width, imageHeight = currentPage.height)
        }
        val appliedCorners = page?.corners ?: fallbackCorners

        _uiState.update { state ->
            state.copy(
                currentPageId = page?.id,
                rawImagePath = page?.originalImagePath.orEmpty(),
                processedImagePath = page?.processedImagePath,
                thumbnailPath = page?.thumbnailPath,
                imageWidth = page?.width ?: 0,
                imageHeight = page?.height ?: 0,
                selectedScanMode = page?.scanMode ?: session.scanMode,
                appliedScanMode = page?.scanMode ?: session.scanMode,
                detectedCorners = page?.corners,
                appliedCorners = appliedCorners,
                editableCorners = appliedCorners,
                rotationDegrees = page?.rotationDegrees ?: 0,
                reviewStatus = page?.reviewStatus,
                pendingPageCount = session.pendingPageCount,
                acceptedPageCount = session.acceptedPageCount,
                isProcessing = false,
                isSaving = false,
                showOriginal = false,
                isCropAdjustmentVisible = false,
                errorMessage = errorMessage
            )
        }
    }

    private fun acceptBlockedMessage(state: ReviewUiState): String = when {
        state.processedImagePath.isNullOrBlank() -> "Process this page before accepting it."
        state.hasPendingScanModeChange || state.hasPendingCropChange -> "Apply pending changes before accepting."
        else -> "This page cannot be accepted yet."
    }

    private val ScannedPage.needsReviewProcessing: Boolean
        get() = reviewStatus == PageReviewStatus.PENDING && processedImagePath.isNullOrBlank()

    private fun ScanSession.firstPendingPage(): ScannedPage? = pages
        .sortedBy { page -> page.pageIndex }
        .firstOrNull { page -> page.reviewStatus == PageReviewStatus.PENDING }

    private fun ScanSession.latestPage(): ScannedPage? = pages.maxByOrNull { page -> page.pageIndex }

    private val ScanSession.pendingPageCount: Int
        get() = pages.count { page -> page.reviewStatus == PageReviewStatus.PENDING }

    private val ScanSession.acceptedPageCount: Int
        get() = pages.count { page -> page.reviewStatus == PageReviewStatus.ACCEPTED }

    private fun Int.nextRightAngleRotation(): Int = (this + RIGHT_ANGLE_DEGREES) % FULL_ROTATION_DEGREES

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
        const val RIGHT_ANGLE_DEGREES = 90
        const val FULL_ROTATION_DEGREES = 360
    }
}
