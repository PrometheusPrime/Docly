package com.docly.app.feature.library

sealed interface LibraryUiEffect {
    data class ShowToast(val message: String) : LibraryUiEffect
}
