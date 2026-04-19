package com.docly.app.feature.scanner

sealed interface ScannerUiEffect {
    data class NavigateToReview(val sessionId: String) : ScannerUiEffect

    data class ShowToast(val message: String) : ScannerUiEffect
}
