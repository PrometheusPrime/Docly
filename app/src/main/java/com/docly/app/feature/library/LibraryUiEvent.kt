package com.docly.app.feature.library

import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.SortMode
import com.docly.app.domain.model.ViewMode

sealed interface LibraryUiEvent {
    data object OnLoad : LibraryUiEvent
    data object OnImportDocumentClicked : LibraryUiEvent
    data class OnImportDocumentSelected(val uriString: String) : LibraryUiEvent
    data class OnSearchQueryChanged(val query: String) : LibraryUiEvent
    data object OnClearSearchClicked : LibraryUiEvent
    data class OnSortModeChanged(val sortMode: SortMode) : LibraryUiEvent
    data class OnTypeFilterChanged(val documentType: DocumentType?) : LibraryUiEvent
    data object OnFavoriteFilterToggled : LibraryUiEvent
    data class OnViewModeChanged(val viewMode: ViewMode) : LibraryUiEvent
    data class OnOpenDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnShareDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnFavoriteDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnRenameDocumentClicked(val documentId: String) : LibraryUiEvent
    data class OnRenameDocumentNameChanged(val name: String) : LibraryUiEvent
    data object OnRenameDocumentConfirmed : LibraryUiEvent
    data object OnRenameDocumentDismissed : LibraryUiEvent
    data class OnDeleteDocumentClicked(val documentId: String) : LibraryUiEvent
    data object OnDeleteDocumentConfirmed : LibraryUiEvent
    data object OnDeleteDocumentDismissed : LibraryUiEvent
}
