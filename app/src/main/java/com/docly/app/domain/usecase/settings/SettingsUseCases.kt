package com.docly.app.domain.usecase.settings

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.StorageUsage
import com.docly.app.domain.model.ThemeMode
import com.docly.app.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.settings
}

class GetSettingsUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(): AppResult<AppSettings> = settingsRepository.getSettings()
}

class ObserveStorageUsageUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    operator fun invoke(): Flow<StorageUsage> = settingsRepository.observeStorageUsage()
}

class UpdateThemeModeUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(themeMode: ThemeMode): AppResult<Unit> = settingsRepository.updateThemeMode(themeMode)
}

class UpdateDynamicColorUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> =
        settingsRepository.updateDynamicColorEnabled(enabled)
}

class UpdateDefaultScanModeUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(scanMode: ScanMode): AppResult<Unit> =
        settingsRepository.updateDefaultScanMode(scanMode)
}

class UpdateAutoCaptureUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> =
        settingsRepository.updateAutoCaptureEnabled(enabled)
}

class UpdateReaderTextSizeUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(textSizeSp: Float): AppResult<Unit> =
        settingsRepository.updateReaderTextSizeSp(textSizeSp)
}

class UpdateReaderThemeUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(readerThemeMode: ReaderThemeMode): AppResult<Unit> =
        settingsRepository.updateReaderThemeMode(readerThemeMode)
}

class UpdateDefaultPdfQualityUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(quality: PdfExportQuality): AppResult<Unit> =
        settingsRepository.updateDefaultPdfQuality(quality)
}

class ClearCacheUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(): AppResult<Unit> = settingsRepository.clearCache()
}
