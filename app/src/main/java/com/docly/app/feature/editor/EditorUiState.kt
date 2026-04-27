package com.docly.app.feature.editor

import com.docly.app.domain.model.ScannedPage

data class EditorUiState(
    val sessionId: String = "",
    val pages: List<ScannedPage> = emptyList(),
    val pendingPageCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val canEditPages: Boolean
        get() = sessionId.isNotBlank() && !isLoading && !isSaving

    val canContinue: Boolean
        get() = pages.isNotEmpty() && pendingPageCount == 0 && canEditPages
}
