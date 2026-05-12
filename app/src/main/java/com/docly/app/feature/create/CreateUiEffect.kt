package com.docly.app.feature.create

sealed interface CreateUiEffect {
    data class NavigateToEditor(val documentId: String) : CreateUiEffect
    data object NavigateToScanner : CreateUiEffect
    data class ShowToast(val message: String) : CreateUiEffect
}
