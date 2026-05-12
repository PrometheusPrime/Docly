package com.docly.app.feature.documenteditor

sealed interface DocumentEditorUiEvent {
    data object OnStart : DocumentEditorUiEvent
    data object OnRetryClicked : DocumentEditorUiEvent
    data class OnContentChanged(val content: String) : DocumentEditorUiEvent
    data class OnEditorModeChanged(val mode: DocumentEditorMode) : DocumentEditorUiEvent
    data class OnSearchQueryChanged(val query: String) : DocumentEditorUiEvent
    data object OnPreviousSearchResultClicked : DocumentEditorUiEvent
    data object OnNextSearchResultClicked : DocumentEditorUiEvent
    data object OnSaveClicked : DocumentEditorUiEvent
    data object OnExportPdfClicked : DocumentEditorUiEvent
    data object OnNavigateBackClicked : DocumentEditorUiEvent
    data object OnDiscardChangesConfirmed : DocumentEditorUiEvent
    data object OnUnsavedChangesDismissed : DocumentEditorUiEvent
}
