package com.docly.app.feature.library

import com.docly.app.domain.model.SavedDocument

data class LibraryUiState(
    val documents: List<SavedDocument> = emptyList(),
    val totalDocumentCount: Int = documents.size,
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val pendingDeleteDocument: SavedDocument? = null,
    val errorMessage: String? = null
) {
    val hasDocuments: Boolean
        get() = totalDocumentCount > 0

    val hasActiveSearch: Boolean
        get() = searchQuery.isNotBlank()
}
