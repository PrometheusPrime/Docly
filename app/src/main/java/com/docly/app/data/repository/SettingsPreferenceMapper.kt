package com.docly.app.data.repository

import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ThemeMode

internal fun settingsFromPreferences(
    themeModeName: String?,
    dynamicColorEnabled: Boolean?,
    defaultScanModeName: String?,
    autoCaptureEnabled: Boolean?,
    readerTextSizeSp: Float?,
    readerThemeModeName: String?,
    defaultPdfQualityName: String?
): AppSettings = AppSettings(
    themeMode = enumValue(themeModeName, ThemeMode.SYSTEM),
    dynamicColorEnabled = dynamicColorEnabled ?: false,
    defaultScanMode = enumValue(defaultScanModeName, ScanMode.DOCUMENT),
    autoCaptureEnabled = autoCaptureEnabled ?: false,
    readerTextSizeSp = (readerTextSizeSp ?: AppSettings.DEFAULT_READER_TEXT_SIZE_SP).coerceIn(
        AppSettings.MIN_READER_TEXT_SIZE_SP,
        AppSettings.MAX_READER_TEXT_SIZE_SP
    ),
    readerThemeMode = enumValue(readerThemeModeName, ReaderThemeMode.LIGHT),
    defaultPdfQuality = enumValue(defaultPdfQualityName, PdfExportQuality.HIGH)
)

private inline fun <reified T : Enum<T>> enumValue(value: String?, defaultValue: T): T = value?.let { rawValue ->
    enumValues<T>().firstOrNull { candidate -> candidate.name == rawValue }
} ?: defaultValue
