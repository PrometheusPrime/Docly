package com.docly.app.feature.library

sealed interface LibraryUiEvent {
    data object OnLoad : LibraryUiEvent
    data class OnSearchQueryChanged(val query: String) : LibraryUiEvent
    data object OnClearSearchClicked : LibraryUiEvent
    data class OnOpenDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnShareDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnDeleteDocumentClicked(val documentId: String) : LibraryUiEvent
    data object OnDeleteDocumentConfirmed : LibraryUiEvent
    data object OnDeleteDocumentDismissed : LibraryUiEvent
}
