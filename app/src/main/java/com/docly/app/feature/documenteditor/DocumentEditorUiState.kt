package com.docly.app.feature.documenteditor

import com.docly.app.domain.model.DocumentType

data class DocumentEditorUiState(
    val documentId: String = "",
    val title: String = "Edit document",
    val documentType: DocumentType? = null,
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isExportingPdf: Boolean = false,
    val hasLoadedContent: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null
) {
    val canEdit: Boolean
        get() = hasLoadedContent && !isLoading && !isSaving && !isExportingPdf

    val canSave: Boolean
        get() = canEdit && isDirty

    val canExportPdf: Boolean
        get() = hasLoadedContent && !isLoading && !isSaving && !isExportingPdf
}
