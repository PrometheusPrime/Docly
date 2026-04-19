package com.docly.app.feature.metadata

data class MetadataUiState(
    val sessionId: String = "",
    val grade: String = "",
    val subject: String = "",
    val year: String = "",
    val paperType: String = "",
    val paperNumber: String = "",
    val source: String = "",
    val notes: String = "",
    val generatedFileName: String = "",
    val validationErrors: List<String> = emptyList(),
    val isSaving: Boolean = false
)
