package com.docly.app.feature.metadata

sealed interface MetadataUiEffect {
    data class NavigateToExport(val sessionId: String) : MetadataUiEffect
    data class ShowToast(val message: String) : MetadataUiEffect
}
