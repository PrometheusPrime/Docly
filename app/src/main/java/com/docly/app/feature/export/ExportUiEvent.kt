package com.docly.app.feature.export

sealed interface ExportUiEvent {
    data object OnLoad : ExportUiEvent
    data object OnExportClicked : ExportUiEvent
    data object OnOpenLibraryClicked : ExportUiEvent
}
