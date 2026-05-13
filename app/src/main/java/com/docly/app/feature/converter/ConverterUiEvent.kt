package com.docly.app.feature.converter

import com.docly.app.domain.model.DocumentType

sealed interface ConverterUiEvent {
    data object OnStart : ConverterUiEvent
    data class OnInputSelected(val documentId: String) : ConverterUiEvent
    data class OnOutputTypeSelected(val outputType: DocumentType) : ConverterUiEvent
    data class OnOutputFileNameChanged(val outputFileName: String) : ConverterUiEvent
    data class OnXlsxSheetSelected(val sheetIndex: Int) : ConverterUiEvent
    data object OnConvertClicked : ConverterUiEvent
    data object OnOpenResultClicked : ConverterUiEvent
    data object OnShareResultClicked : ConverterUiEvent
    data object OnViewDocumentsClicked : ConverterUiEvent
}
