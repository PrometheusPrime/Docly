package com.docly.app.feature.scanner

import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

data class ScannerUiState(
    val cameraPermissionStatus: CameraPermissionStatus = CameraPermissionStatus.NotRequested,
    val isCameraReady: Boolean = false,
    val isCapturing: Boolean = false,
    val isImporting: Boolean = false,
    val isFlashAvailable: Boolean = false,
    val isFlashEnabled: Boolean = false,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val detectedCorners: PageCorners? = null,
    val previewBoundary: PreviewDocumentBoundary? = null,
    val qualityHint: String? = null,
    val sessionId: String? = null,
    val errorMessage: String? = null
) {
    val isCameraPermissionGranted: Boolean
        get() = cameraPermissionStatus == CameraPermissionStatus.Granted
}
