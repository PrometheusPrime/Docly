package com.docly.app.feature.library

sealed interface LibraryUiEffect {
    data class ShowToast(val message: String) : LibraryUiEffect
    data class OpenPdf(val pdfPath: String) : LibraryUiEffect
    data class SharePdf(val pdfPath: String, val title: String) : LibraryUiEffect
}
