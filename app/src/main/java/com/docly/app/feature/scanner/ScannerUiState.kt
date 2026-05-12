package com.docly.app.feature.scanner

import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionRecoveryDestination

data class ScannerUiState(
    val cameraPermissionStatus: CameraPermissionStatus = CameraPermissionStatus.NotRequested,
    val isCameraReady: Boolean = false,
    val isLaunchingScanner: Boolean = false,
    val isCapturing: Boolean = false,
    val isImporting: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val isFlashEnabled: Boolean = false,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val isAutoCaptureEnabled: Boolean = false,
    val detectedCorners: PageCorners? = null,
    val previewBoundary: PreviewDocumentBoundary? = null,
    val qualityHint: String? = null,
    val autoCaptureHint: String? = null,
    val autoCaptureRequestId: Long = 0L,
    val sessionId: String? = null,
    val recoveryPrompt: ScannerRecoveryPrompt? = null,
    val isCheckingRecovery: Boolean = false,
    val isDiscardingRecovery: Boolean = false,
    val errorMessage: String? = null
) {
    val isCameraPermissionGranted: Boolean
        get() = cameraPermissionStatus == CameraPermissionStatus.Granted

    val hasRecoveryPrompt: Boolean
        get() = recoveryPrompt != null
}

data class ScannerRecoveryPrompt(
    val sessionId: String,
    val pageCount: Int,
    val destination: ScanSessionRecoveryDestination
)
