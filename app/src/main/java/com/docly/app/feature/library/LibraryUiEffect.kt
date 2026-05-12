package com.docly.app.feature.library

sealed interface LibraryUiEffect {
    data class ShowToast(val message: String) : LibraryUiEffect
    data object LaunchImportPicker : LibraryUiEffect
    data class OpenReader(val documentId: String) : LibraryUiEffect
    data class OpenEditor(val documentId: String) : LibraryUiEffect
    data class OpenPdfPageEditor(val documentId: String) : LibraryUiEffect
    data class OpenDocument(val filePath: String, val mimeType: String?) : LibraryUiEffect
    data class ShareDocument(val filePath: String, val title: String, val mimeType: String?) : LibraryUiEffect
}
