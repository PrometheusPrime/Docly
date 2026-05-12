package com.docly.app.feature.scanreview

import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.usecase.scanner.ScannedOutputFormat

data class ScanReviewUiState(
    val sessionId: String = "",
    val title: String = "Scanned document",
    val outputFormat: ScannedOutputFormat = ScannedOutputFormat.PDF,
    val pages: List<ScannedPage> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val canEditPages: Boolean
        get() = sessionId.isNotBlank() && !isLoading && !isSaving

    val canSave: Boolean
        get() = title.isNotBlank() && pages.isNotEmpty() && canEditPages
}
