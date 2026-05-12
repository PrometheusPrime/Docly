package com.docly.app.feature.pdfpageeditor

import com.docly.app.domain.model.ScannedPage

data class PdfPageEditorUiState(
    val documentId: String = "",
    val title: String = "Page tools",
    val pages: List<ScannedPage> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null
) {
    val canEditPages: Boolean
        get() = documentId.isNotBlank() && !isLoading && !isSaving

    val canSave: Boolean
        get() = canEditPages && pages.isNotEmpty() && isDirty
}
