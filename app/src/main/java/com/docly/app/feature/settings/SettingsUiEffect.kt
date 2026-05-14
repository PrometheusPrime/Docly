package com.docly.app.feature.settings

sealed interface SettingsUiEffect {
    data class ShowToast(val message: String) : SettingsUiEffect
}
