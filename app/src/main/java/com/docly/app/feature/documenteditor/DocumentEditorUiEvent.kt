package com.docly.app.feature.documenteditor

sealed interface DocumentEditorUiEvent {
    data object OnStart : DocumentEditorUiEvent
    data object OnRetryClicked : DocumentEditorUiEvent
    data class OnContentChanged(val content: String) : DocumentEditorUiEvent
    data object OnSaveClicked : DocumentEditorUiEvent
    data object OnExportPdfClicked : DocumentEditorUiEvent
}
