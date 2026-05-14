package com.docly.app.feature.settings

import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ThemeMode

sealed interface SettingsUiEvent {
    data class OnThemeModeSelected(val themeMode: ThemeMode) : SettingsUiEvent
    data class OnDynamicColorChanged(val enabled: Boolean) : SettingsUiEvent
    data class OnDefaultScanModeSelected(val scanMode: ScanMode) : SettingsUiEvent
    data class OnAutoCaptureChanged(val enabled: Boolean) : SettingsUiEvent
    data class OnReaderTextSizeChanged(val textSizeSp: Float) : SettingsUiEvent
    data class OnReaderThemeSelected(val readerThemeMode: ReaderThemeMode) : SettingsUiEvent
    data class OnDefaultPdfQualitySelected(val quality: PdfExportQuality) : SettingsUiEvent
    data object OnRefreshStorageClicked : SettingsUiEvent
    data object OnClearCacheClicked : SettingsUiEvent
}
