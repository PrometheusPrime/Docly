package com.docly.app.feature.library

import com.docly.app.domain.model.SavedDocument

data class LibraryUiState(
    val documents: List<SavedDocument> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
