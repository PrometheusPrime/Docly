package com.docly.app.feature.scanner

import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.core.camera.PreviewFrameAnalysis
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

sealed interface ScannerUiEvent {
    data object OnStart : ScannerUiEvent
    data object OnScannerLaunchStarted : ScannerUiEvent
    data class OnScannerLaunchFailed(val message: String) : ScannerUiEvent
    data class OnScanResult(val pageImageUris: List<String>) : ScannerUiEvent
    data object OnScanCanceled : ScannerUiEvent
    data class OnPermissionResult(val status: CameraPermissionStatus) : ScannerUiEvent
    data class OnCameraReadyChanged(val ready: Boolean) : ScannerUiEvent
    data class OnCameraPreviewError(val message: String) : ScannerUiEvent
    data class OnFlashAvailabilityChanged(val available: Boolean) : ScannerUiEvent
    data object OnFlashToggleClicked : ScannerUiEvent
    data class OnCaptureClicked(val captureAction: ScannerCaptureAction) : ScannerUiEvent
    data class OnImportPhotosSelected(val sourceUris: List<String>) : ScannerUiEvent
    data class OnAutoCaptureEnabledChanged(val enabled: Boolean) : ScannerUiEvent
    data object OnResumeRecoveredSessionClicked : ScannerUiEvent
    data object OnDiscardRecoveredSessionClicked : ScannerUiEvent
    data class OnScanModeChanged(val scanMode: ScanMode) : ScannerUiEvent
    data class OnCornersDetected(val corners: PageCorners?) : ScannerUiEvent
    data class OnPreviewDocumentBoundaryChanged(val boundary: PreviewDocumentBoundary?) : ScannerUiEvent
    data class OnPreviewFrameAnalysisChanged(val analysis: PreviewFrameAnalysis?) : ScannerUiEvent
}
