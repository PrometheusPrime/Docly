package com.docly.app.data.repository

import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferenceMapperTest {
    @Test
    fun missingValuesUseCurrentBehaviorDefaults() {
        val settings = settingsFromPreferences(
            themeModeName = null,
            dynamicColorEnabled = null,
            defaultScanModeName = null,
            autoCaptureEnabled = null,
            readerTextSizeSp = null,
            readerThemeModeName = null,
            defaultPdfQualityName = null
        )

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(false, settings.dynamicColorEnabled)
        assertEquals(ScanMode.DOCUMENT, settings.defaultScanMode)
        assertEquals(false, settings.autoCaptureEnabled)
        assertEquals(AppSettings.DEFAULT_READER_TEXT_SIZE_SP, settings.readerTextSizeSp)
        assertEquals(ReaderThemeMode.LIGHT, settings.readerThemeMode)
        assertEquals(PdfExportQuality.HIGH, settings.defaultPdfQuality)
    }

    @Test
    fun invalidEnumValuesFallBackToDefaultsAndReaderSizeIsClamped() {
        val settings = settingsFromPreferences(
            themeModeName = "BLUE",
            dynamicColorEnabled = true,
            defaultScanModeName = "FAST",
            autoCaptureEnabled = true,
            readerTextSizeSp = 100f,
            readerThemeModeName = "SEPIA",
            defaultPdfQualityName = "LOSSLESS"
        )

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(true, settings.dynamicColorEnabled)
        assertEquals(ScanMode.DOCUMENT, settings.defaultScanMode)
        assertEquals(true, settings.autoCaptureEnabled)
        assertEquals(AppSettings.MAX_READER_TEXT_SIZE_SP, settings.readerTextSizeSp)
        assertEquals(ReaderThemeMode.LIGHT, settings.readerThemeMode)
        assertEquals(PdfExportQuality.HIGH, settings.defaultPdfQuality)
    }
}
