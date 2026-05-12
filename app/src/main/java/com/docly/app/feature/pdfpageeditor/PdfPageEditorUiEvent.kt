package com.docly.app.feature.pdfpageeditor

sealed interface PdfPageEditorUiEvent {
    data object OnLoad : PdfPageEditorUiEvent
    data class OnDeletePageClicked(val pageId: String) : PdfPageEditorUiEvent
    data class OnRotatePageClicked(val pageId: String) : PdfPageEditorUiEvent
    data class OnMovePageUp(val pageId: String) : PdfPageEditorUiEvent
    data class OnMovePageDown(val pageId: String) : PdfPageEditorUiEvent
    data object OnSaveClicked : PdfPageEditorUiEvent
}
