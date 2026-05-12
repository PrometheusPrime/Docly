package com.docly.app.feature.scanreview

import com.docly.app.domain.usecase.scanner.ScannedOutputFormat

sealed interface ScanReviewUiEvent {
    data object OnLoad : ScanReviewUiEvent
    data class OnTitleChanged(val title: String) : ScanReviewUiEvent
    data class OnOutputFormatChanged(val outputFormat: ScannedOutputFormat) : ScanReviewUiEvent
    data object OnAddPageClicked : ScanReviewUiEvent
    data class OnDeletePageClicked(val pageId: String) : ScanReviewUiEvent
    data class OnRotatePageClicked(val pageId: String) : ScanReviewUiEvent
    data class OnMovePageUp(val pageId: String) : ScanReviewUiEvent
    data class OnMovePageDown(val pageId: String) : ScanReviewUiEvent
    data object OnSaveClicked : ScanReviewUiEvent
}
