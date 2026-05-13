package com.docly.app.feature.converter

sealed interface ConverterUiEffect {
    data class ShowToast(val message: String) : ConverterUiEffect
    data class NavigateToReader(val documentId: String) : ConverterUiEffect
    data class ShareDocument(val filePath: String, val title: String, val mimeType: String?) : ConverterUiEffect
    data object NavigateToDocuments : ConverterUiEffect
}
