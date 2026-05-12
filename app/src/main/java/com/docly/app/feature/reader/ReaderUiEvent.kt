package com.docly.app.feature.reader

sealed interface ReaderUiEvent {
    data object OnStart : ReaderUiEvent
    data object OnRetryClicked : ReaderUiEvent
    data object OnPdfPreviousPageClicked : ReaderUiEvent
    data object OnPdfNextPageClicked : ReaderUiEvent
    data object OnPdfZoomInClicked : ReaderUiEvent
    data object OnPdfZoomOutClicked : ReaderUiEvent
    data class OnPdfTargetWidthChanged(val widthPx: Int) : ReaderUiEvent
    data object OnLoadMoreTextClicked : ReaderUiEvent
    data object OnTextSizeIncreaseClicked : ReaderUiEvent
    data object OnTextSizeDecreaseClicked : ReaderUiEvent
    data object OnReaderThemeToggled : ReaderUiEvent
    data class OnXlsxSheetSelected(val sheetIndex: Int) : ReaderUiEvent
    data object OnLoadMoreRowsClicked : ReaderUiEvent
}
