package com.docly.app.feature.editor

sealed interface EditorUiEffect {
    data class NavigateToScanner(val sessionId: String) : EditorUiEffect
    data class NavigateToMetadata(val sessionId: String) : EditorUiEffect
    data class ShowToast(val message: String) : EditorUiEffect
}
