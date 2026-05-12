package com.docly.app.feature.scanreview

sealed interface ScanReviewUiEffect {
    data class NavigateToScanner(val sessionId: String) : ScanReviewUiEffect
    data object NavigateToDocuments : ScanReviewUiEffect
    data class ShowToast(val message: String) : ScanReviewUiEffect
}
