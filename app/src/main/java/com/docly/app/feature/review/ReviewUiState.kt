package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

data class ReviewUiState(
    val sessionId: String = "",
    val rawImagePath: String = "",
    val processedImagePath: String? = null,
    val selectedScanMode: ScanMode = ScanMode.DOCUMENT,
    val detectedCorners: PageCorners? = null,
    val editableCorners: PageCorners? = null,
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val showOriginal: Boolean = false,
    val rotationDegrees: Int = 0,
    val errorMessage: String? = null
)
