package com.docly.app.feature.library

import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.SortMode
import com.docly.app.domain.model.ViewMode

data class LibraryUiState(
    val documents: List<DoclyDocument> = emptyList(),
    val totalDocumentCount: Int = documents.size,
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.UPDATED_DESC,
    val typeFilter: DocumentType? = null,
    val favoritesOnly: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val isDeleting: Boolean = false,
    val isRenaming: Boolean = false,
    val pendingDeleteDocument: DoclyDocument? = null,
    val pendingRenameDocument: DoclyDocument? = null,
    val pendingRenameName: String = "",
    val errorMessage: String? = null
) {
    val hasDocuments: Boolean
        get() = totalDocumentCount > 0

    val hasActiveSearch: Boolean
        get() = searchQuery.isNotBlank()

    val hasActiveFilter: Boolean
        get() = typeFilter != null || favoritesOnly
}
