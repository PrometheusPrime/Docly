package com.docly.app.feature.library

sealed interface LibraryUiEvent {
    data object OnLoad : LibraryUiEvent
    data class OnSearchQueryChanged(val query: String) : LibraryUiEvent
    data class OnDeleteDocumentClicked(val documentId: String) : LibraryUiEvent
}
