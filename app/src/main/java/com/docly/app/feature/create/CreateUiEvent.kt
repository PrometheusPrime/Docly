package com.docly.app.feature.create

import com.docly.app.domain.model.DocumentType

sealed interface CreateUiEvent {
    data class OnTitleChanged(val title: String) : CreateUiEvent
    data class OnTypeSelected(val type: DocumentType) : CreateUiEvent
    data object OnCreateClicked : CreateUiEvent
    data object OnCreatePdfFromScanClicked : CreateUiEvent
}
