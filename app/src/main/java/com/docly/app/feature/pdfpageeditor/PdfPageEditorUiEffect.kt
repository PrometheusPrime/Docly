package com.docly.app.feature.pdfpageeditor

sealed interface PdfPageEditorUiEffect {
    data class NavigateToReader(val documentId: String) : PdfPageEditorUiEffect
    data class ShowToast(val message: String) : PdfPageEditorUiEffect
}
