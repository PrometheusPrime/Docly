package com.docly.app.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.usecase.page.ImportDevicePhotosUseCase
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
class ScannerViewModel @Inject constructor(private val importDevicePhotosUseCase: ImportDevicePhotosUseCase) :
    ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ScannerUiEffect>()
    val uiEffect: SharedFlow<ScannerUiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            ScannerUiEvent.OnStart -> Unit

            is ScannerUiEvent.OnPermissionResult -> onPermissionResult(event.status)

            is ScannerUiEvent.OnCameraReadyChanged -> _uiState.update { state ->
                state.copy(
                    isCameraReady = event.ready,
                    isFlashEnabled = if (event.ready) state.isFlashEnabled else false
                )
            }

            is ScannerUiEvent.OnCameraPreviewError -> _uiState.update { state ->
                state.copy(
                    isCameraReady = false,
                    isFlashAvailable = false,
                    isFlashEnabled = false,
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

            ScannerUiEvent.OnCaptureClicked -> showMessage("Camera capture is not implemented yet.")

            is ScannerUiEvent.OnImportPhotosSelected -> importPhotos(event.sourceUris)

            is ScannerUiEvent.OnScanModeChanged -> _uiState.update { state ->
                state.copy(scanMode = event.scanMode)
            }

            is ScannerUiEvent.OnCornersDetected -> _uiState.update { state ->
                state.copy(detectedCorners = event.corners)
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
                errorMessage = if (status == CameraPermissionStatus.Granted) null else state.errorMessage
            )
        }
    }

    private fun importPhotos(sourceUris: List<String>) {
        if (sourceUris.isEmpty() || _uiState.value.isImporting) return

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

    private fun showMessage(message: String) {
        viewModelScope.launch {
            _uiEffect.emit(ScannerUiEffect.ShowToast(message))
        }
    }
}
