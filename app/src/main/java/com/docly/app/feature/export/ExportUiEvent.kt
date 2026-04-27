package com.docly.app.feature.export

sealed interface ExportUiEvent {
    data object OnLoad : ExportUiEvent
    data object OnExportClicked : ExportUiEvent
    data object OnOpenPdfClicked : ExportUiEvent
    data object OnSharePdfClicked : ExportUiEvent
    data object OnOpenLibraryClicked : ExportUiEvent
}
