package com.docly.app.feature.editor

sealed interface EditorUiEvent {
    data object OnLoad : EditorUiEvent
    data object OnAddPageClicked : EditorUiEvent
    data class OnDeletePageClicked(val pageId: String) : EditorUiEvent
    data class OnRotatePageClicked(val pageId: String) : EditorUiEvent
    data class OnMovePageUp(val pageId: String) : EditorUiEvent
    data class OnMovePageDown(val pageId: String) : EditorUiEvent
    data object OnContinueClicked : EditorUiEvent
}
