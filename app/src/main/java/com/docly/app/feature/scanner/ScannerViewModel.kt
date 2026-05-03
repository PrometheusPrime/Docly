package com.docly.app.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.RecoverableScanSession
import com.docly.app.domain.model.ScanSessionRecoveryDestination
import com.docly.app.domain.usecase.page.CapturePageUseCase
import com.docly.app.domain.usecase.page.ImportDevicePhotosUseCase
import com.docly.app.domain.usecase.session.AbandonScanSessionUseCase
import com.docly.app.domain.usecase.session.CleanOrphanedFilesUseCase
import com.docly.app.domain.usecase.session.GetRecoverableSessionUseCase
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
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val capturePageUseCase: CapturePageUseCase,
    private val importDevicePhotosUseCase: ImportDevicePhotosUseCase,
    private val getRecoverableSessionUseCase: GetRecoverableSessionUseCase,
    private val abandonScanSessionUseCase: AbandonScanSessionUseCase,
    private val cleanOrphanedFilesUseCase: CleanOrphanedFilesUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScannerUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY))
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ScannerUiEffect>()
    val uiEffect: SharedFlow<ScannerUiEffect> = _uiEffect.asSharedFlow()

    private var hasStarted = false

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            ScannerUiEvent.OnStart -> start()

            is ScannerUiEvent.OnPermissionResult -> onPermissionResult(event.status)

            is ScannerUiEvent.OnCameraReadyChanged -> _uiState.update { state ->
                state.copy(
                    isCameraReady = event.ready,
                    isFlashEnabled = if (event.ready) state.isFlashEnabled else false,
                    previewBoundary = if (event.ready) state.previewBoundary else null
                )
            }

            is ScannerUiEvent.OnCameraPreviewError -> _uiState.update { state ->
                state.copy(
                    isCameraReady = false,
                    isFlashAvailable = false,
                    isFlashEnabled = false,
                    previewBoundary = null,
                    errorMessage = event.message
                )
            }

            is ScannerUiEvent.OnFlashAvailabilityChanged -> _uiState.update { state ->
                state.copy(
                    isFlashAvailable = event.available,
                    isFlashEnabled = if (event.available) state.isFlashEnabled else false
                )
            }

            ScannerUiEvent.OnFlashToggleClicked -> _uiState.update { state ->
                if (state.isCameraReady && state.isFlashAvailable) {
                    state.copy(isFlashEnabled = !state.isFlashEnabled)
                } else {
                    state
                }
            }

            is ScannerUiEvent.OnCaptureClicked -> capturePage(event.captureAction)

            is ScannerUiEvent.OnImportPhotosSelected -> importPhotos(event.sourceUris)

            ScannerUiEvent.OnResumeRecoveredSessionClicked -> resumeRecoveredSession()

            ScannerUiEvent.OnDiscardRecoveredSessionClicked -> discardRecoveredSession()

            is ScannerUiEvent.OnScanModeChanged -> _uiState.update { state ->
                if (state.hasRecoveryPrompt) state else state.copy(scanMode = event.scanMode)
            }

            is ScannerUiEvent.OnCornersDetected -> _uiState.update { state ->
                state.copy(detectedCorners = event.corners)
            }

            is ScannerUiEvent.OnPreviewDocumentBoundaryChanged -> _uiState.update { state ->
                state.copy(
                    previewBoundary = event.boundary,
                    detectedCorners = event.boundary?.corners ?: state.detectedCorners
                )
            }
        }
    }

    private fun start() {
        if (hasStarted) return
        hasStarted = true

        viewModelScope.launch {
            cleanOrphanedFilesUseCase()
        }

        if (!_uiState.value.sessionId.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isCheckingRecovery = true, errorMessage = null)
            }

            when (val result = getRecoverableSessionUseCase()) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isCheckingRecovery = false, errorMessage = message)
                    }
                    _uiEffect.emit(ScannerUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            recoveryPrompt = result.data?.toPrompt(),
                            isCheckingRecovery = false,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun onPermissionResult(status: CameraPermissionStatus) {
        _uiState.update { state ->
            state.copy(
                cameraPermissionStatus = status,
                isCameraReady = if (status == CameraPermissionStatus.Granted) state.isCameraReady else false,
                isFlashAvailable = if (status == CameraPermissionStatus.Granted) state.isFlashAvailable else false,
                isFlashEnabled = if (status == CameraPermissionStatus.Granted) state.isFlashEnabled else false,
                previewBoundary = if (status == CameraPermissionStatus.Granted) state.previewBoundary else null,
                errorMessage = if (status == CameraPermissionStatus.Granted) null else state.errorMessage
            )
        }
    }

    private fun importPhotos(sourceUris: List<String>) {
        if (
            sourceUris.isEmpty() ||
            _uiState.value.isImporting ||
            _uiState.value.isCapturing ||
            _uiState.value.hasRecoveryPrompt
        ) {
            return
        }

        viewModelScope.launch {
            val currentState = _uiState.value
            _uiState.update { state ->
                state.copy(isImporting = true, errorMessage = null)
            }

            when (
                val result = importDevicePhotosUseCase(
                    sessionId = currentState.sessionId,
                    sourceUris = sourceUris,
                    scanMode = currentState.scanMode
                )
            ) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isImporting = false, errorMessage = message)
                    }
                    _uiEffect.emit(ScannerUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isImporting = false,
                            sessionId = result.data.sessionId,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ScannerUiEffect.NavigateToReview(result.data.sessionId))
                }
            }
        }
    }

    private fun capturePage(captureAction: ScannerCaptureAction) {
        val currentState = _uiState.value
        if (
            currentState.isCapturing ||
            currentState.isImporting ||
            currentState.hasRecoveryPrompt ||
            !currentState.isCameraPermissionGranted ||
            !currentState.isCameraReady
        ) {
            return
        }

        _uiState.update { state ->
            state.copy(isCapturing = true, errorMessage = null)
        }

        viewModelScope.launch {
            when (
                val result = capturePageUseCase(
                    sessionId = currentState.sessionId,
                    scanMode = currentState.scanMode,
                    captureToFile = captureAction::captureToFile
                )
            ) {
                is AppResult.Error -> {
                    val message = result.toCaptureUserMessage()
                    _uiState.update { state ->
                        state.copy(isCapturing = false, errorMessage = message)
                    }
                    _uiEffect.emit(ScannerUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isCapturing = false,
                            sessionId = result.data.sessionId,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ScannerUiEffect.NavigateToReview(result.data.sessionId))
                }
            }
        }
    }

    private fun resumeRecoveredSession() {
        val prompt = _uiState.value.recoveryPrompt ?: return
        _uiState.update { state ->
            state.copy(
                sessionId = prompt.sessionId,
                recoveryPrompt = null,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            when (prompt.destination) {
                ScanSessionRecoveryDestination.REVIEW -> {
                    _uiEffect.emit(ScannerUiEffect.NavigateToReview(prompt.sessionId))
                }

                ScanSessionRecoveryDestination.EDITOR -> {
                    _uiEffect.emit(ScannerUiEffect.NavigateToEditor(prompt.sessionId))
                }

                ScanSessionRecoveryDestination.EXPORT -> {
                    _uiEffect.emit(ScannerUiEffect.NavigateToExport(prompt.sessionId))
                }
            }
        }
    }

    private fun discardRecoveredSession() {
        val prompt = _uiState.value.recoveryPrompt ?: return
        if (_uiState.value.isDiscardingRecovery) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isDiscardingRecovery = true, errorMessage = null)
            }

            when (val result = abandonScanSessionUseCase(prompt.sessionId)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(
                            recoveryPrompt = null,
                            isDiscardingRecovery = false,
                            errorMessage = message
                        )
                    }
                    _uiEffect.emit(ScannerUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            recoveryPrompt = null,
                            isDiscardingRecovery = false,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ScannerUiEffect.ShowToast("Recovered scan discarded."))
                }
            }
        }
    }

    private fun RecoverableScanSession.toPrompt(): ScannerRecoveryPrompt = ScannerRecoveryPrompt(
        sessionId = session.id,
        pageCount = session.pages.size,
        destination = destination
    )

    private fun AppResult.Error.toCaptureUserMessage(): String = when {
        category == AppErrorCategory.STORAGE -> toUserMessage()
        message.isNotBlank() -> message
        else -> toUserMessage()
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
