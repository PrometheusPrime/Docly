package com.docly.app.feature.create

import com.docly.app.domain.model.DocumentType

data class CreateUiState(
    val title: String = "Untitled document",
    val selectedType: DocumentType = DocumentType.TXT,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
) {
    val canCreate: Boolean
        get() = !isCreating && title.isNotBlank() && selectedType in CREATABLE_TYPES
}

val CREATABLE_TYPES: List<DocumentType> = listOf(DocumentType.TXT, DocumentType.MARKDOWN, DocumentType.HTML)
