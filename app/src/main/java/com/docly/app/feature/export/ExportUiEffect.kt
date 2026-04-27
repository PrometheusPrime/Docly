package com.docly.app.feature.export

sealed interface ExportUiEffect {
    data class ShowToast(val message: String) : ExportUiEffect
    data class OpenPdf(val pdfPath: String) : ExportUiEffect
    data class SharePdf(val pdfPath: String, val title: String) : ExportUiEffect
    data object NavigateToLibrary : ExportUiEffect
}
