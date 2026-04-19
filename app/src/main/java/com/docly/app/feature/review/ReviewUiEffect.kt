package com.docly.app.feature.review

sealed interface ReviewUiEffect {
    data class NavigateBackToScanner(val sessionId: String) : ReviewUiEffect
    data class NavigateToEditor(val sessionId: String) : ReviewUiEffect
    data class ShowToast(val message: String) : ReviewUiEffect
}
