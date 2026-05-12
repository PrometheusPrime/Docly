package com.docly.app.feature.documenteditor

sealed interface DocumentEditorUiEffect {
    data class NavigateToReader(val documentId: String) : DocumentEditorUiEffect
    data class ShowToast(val message: String) : DocumentEditorUiEffect
}
