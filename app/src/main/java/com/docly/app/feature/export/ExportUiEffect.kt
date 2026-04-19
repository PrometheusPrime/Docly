package com.docly.app.feature.export

sealed interface ExportUiEffect {
    data class ShowToast(val message: String) : ExportUiEffect
    data object NavigateToLibrary : ExportUiEffect
}
