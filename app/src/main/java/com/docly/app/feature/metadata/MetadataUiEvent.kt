package com.docly.app.feature.metadata

sealed interface MetadataUiEvent {
    data object OnLoad : MetadataUiEvent
    data class OnGradeChanged(val value: String) : MetadataUiEvent
    data class OnSubjectChanged(val value: String) : MetadataUiEvent
    data class OnYearChanged(val value: String) : MetadataUiEvent
    data class OnPaperTypeChanged(val value: String) : MetadataUiEvent
    data class OnPaperNumberChanged(val value: String) : MetadataUiEvent
    data class OnSourceChanged(val value: String) : MetadataUiEvent
    data class OnNotesChanged(val value: String) : MetadataUiEvent
    data object OnContinueClicked : MetadataUiEvent
}
