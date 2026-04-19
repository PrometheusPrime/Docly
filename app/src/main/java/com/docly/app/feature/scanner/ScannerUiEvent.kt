package com.docly.app.feature.scanner

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

sealed interface ScannerUiEvent {
    data object OnStart : ScannerUiEvent
    data class OnPermissionResult(val status: CameraPermissionStatus) : ScannerUiEvent
    data class OnCameraReadyChanged(val ready: Boolean) : ScannerUiEvent
    data class OnCameraPreviewError(val message: String) : ScannerUiEvent
    data class OnFlashAvailabilityChanged(val available: Boolean) : ScannerUiEvent
    data object OnFlashToggleClicked : ScannerUiEvent
    data object OnCaptureClicked : ScannerUiEvent
    data class OnImportPhotosSelected(val sourceUris: List<String>) : ScannerUiEvent
    data class OnScanModeChanged(val scanMode: ScanMode) : ScannerUiEvent
    data class OnCornersDetected(val corners: PageCorners?) : ScannerUiEvent
}
