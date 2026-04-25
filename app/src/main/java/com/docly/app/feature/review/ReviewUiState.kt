package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
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
    val appliedCorners: PageCorners? = null,
    val editableCorners: PageCorners? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val showOriginal: Boolean = false,
    val isCropAdjustmentVisible: Boolean = false,
    val rotationDegrees: Int = 0,
    val reviewStatus: PageReviewStatus? = null,
    val pendingPageCount: Int = 0,
    val acceptedPageCount: Int = 0,
    val errorMessage: String? = null
) {
    val hasCropEditor: Boolean
        get() = canAdjustCrop && isCropAdjustmentVisible && editableCorners != null

    val canAdjustCrop: Boolean
        get() = currentPageId != null &&
            rawImagePath.isNotBlank() &&
            imageWidth > 0 &&
            imageHeight > 0

    val canApplyCrop: Boolean
        get() = hasCropEditor && !isProcessing && !isSaving

    val canSelectScanMode: Boolean
        get() = currentPageId != null && !isProcessing && !isSaving

    val hasPendingScanModeChange: Boolean
        get() = selectedScanMode != appliedScanMode

    val hasPendingCropChange: Boolean
        get() = editableCorners != null && appliedCorners != null && editableCorners != appliedCorners

    val canResetToDetected: Boolean
        get() = hasCropEditor && detectedCorners != null && !isProcessing && !isSaving

    val canResetToFullImage: Boolean
        get() = hasCropEditor && imageWidth > 0 && imageHeight > 0 && !isProcessing && !isSaving

    val canRotate: Boolean
        get() = currentPageId != null && !isProcessing && !isSaving && !isCropAdjustmentVisible

    val canToggleOriginal: Boolean
        get() = currentPageId != null && processedImagePath != null && !isProcessing && !isSaving

    val canAccept: Boolean
        get() = currentPageId != null &&
            !processedImagePath.isNullOrBlank() &&
            !isProcessing &&
            !isSaving &&
            !hasPendingScanModeChange &&
            !hasPendingCropChange

    val canDiscardForRescan: Boolean
        get() = currentPageId != null && !isProcessing && !isSaving
}
