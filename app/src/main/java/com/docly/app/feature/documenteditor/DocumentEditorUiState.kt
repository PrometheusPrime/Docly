package com.docly.app.feature.documenteditor

import com.docly.app.domain.model.DocumentType

data class DocumentEditorUiState(
    val documentId: String = "",
    val title: String = "Edit document",
    val documentType: DocumentType? = null,
    val content: String = "",
    val previewHtml: String = "",
    val editorMode: DocumentEditorMode = DocumentEditorMode.SOURCE,
    val saveStatus: DocumentEditorSaveStatus = DocumentEditorSaveStatus.SAVED,
    val lastSavedAt: Long? = null,
    val searchQuery: String = "",
    val searchResultCount: Int = 0,
    val currentSearchResultIndex: Int = 0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isAutosaving: Boolean = false,
    val isPreviewLoading: Boolean = false,
    val isExportingPdf: Boolean = false,
    val hasLoadedContent: Boolean = false,
    val isDirty: Boolean = false,
    val showUnsavedChangesDialog: Boolean = false,
    val errorMessage: String? = null
) {
    val canEdit: Boolean
        get() = hasLoadedContent &&
            !isLoading &&
            !isSaving &&
            !isExportingPdf &&
            editorMode == DocumentEditorMode.SOURCE

    val canSave: Boolean
        get() = hasLoadedContent && !isLoading && !isSaving && !isExportingPdf && isDirty

    val canExportPdf: Boolean
        get() = hasLoadedContent && !isLoading && !isSaving && !isAutosaving && !isExportingPdf

    val canPreview: Boolean
        get() = documentType == DocumentType.MARKDOWN || documentType == DocumentType.HTML

    val hasSearchResults: Boolean
        get() = searchQuery.isNotBlank() && searchResultCount > 0

    val searchSummary: String
        get() = when {
            searchQuery.isBlank() -> ""
            searchResultCount == 0 -> "No matches"
            else -> "${currentSearchResultIndex + 1} of $searchResultCount"
        }
}

enum class DocumentEditorMode {
    SOURCE,
    PREVIEW
}

enum class DocumentEditorSaveStatus {
    SAVED,
    UNSAVED,
    AUTOSAVING,
    SAVING,
    ERROR
}
