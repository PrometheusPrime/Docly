package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ScanMode

sealed interface ReviewUiEvent {
    data object OnLoad : ReviewUiEvent
    data class OnScanModeChanged(val scanMode: ScanMode) : ReviewUiEvent
    data class OnCornersChanged(val corners: PageCorners) : ReviewUiEvent
    data object OnReprocessClicked : ReviewUiEvent
    data object OnRotateClicked : ReviewUiEvent
    data object OnAcceptClicked : ReviewUiEvent
    data object OnRescanClicked : ReviewUiEvent
    data object OnToggleOriginalClicked : ReviewUiEvent
}
