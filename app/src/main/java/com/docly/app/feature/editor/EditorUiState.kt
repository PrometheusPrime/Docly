package com.docly.app.feature.editor

import com.docly.app.domain.model.ScannedPage

data class EditorUiState(
    val sessionId: String = "",
    val pages: List<ScannedPage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
