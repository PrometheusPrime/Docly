package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

data class ReviewUiState(
    val sessionId: String = "",
    val currentPageId: String? = null,
    val rawImagePath: String = "",
    val processedImagePath: String? = null,
    val thumbnailPath: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val selectedScanMode: ScanMode = ScanMode.DOCUMENT,
    val appliedScanMode: ScanMode = ScanMode.DOCUMENT,
    val detectedCorners: PageCorners? = null,
    val editableCorners: PageCorners? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val showOriginal: Boolean = false,
    val rotationDegrees: Int = 0,
    val errorMessage: String? = null
) {
    val hasCropEditor: Boolean
        get() = currentPageId != null &&
            rawImagePath.isNotBlank() &&
            imageWidth > 0 &&
            imageHeight > 0 &&
            editableCorners != null

    val canApplyCrop: Boolean
        get() = hasCropEditor && !isProcessing && !isSaving

    val canSelectScanMode: Boolean
        get() = currentPageId != null && !isProcessing && !isSaving

    val hasPendingScanModeChange: Boolean
        get() = selectedScanMode != appliedScanMode

    val canResetToDetected: Boolean
        get() = detectedCorners != null && !isProcessing && !isSaving

    val canResetToFullImage: Boolean
        get() = currentPageId != null && imageWidth > 0 && imageHeight > 0 && !isProcessing && !isSaving
}
