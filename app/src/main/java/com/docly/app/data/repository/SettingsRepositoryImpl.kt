package com.docly.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.StorageUsage
import com.docly.app.domain.model.ThemeMode
import com.docly.app.domain.repository.CleanupRepository
import com.docly.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.doclySettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "docly_settings")

class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appFileDirectories: AppFileDirectories,
    private val cleanupRepository: CleanupRepository,
    private val dispatcherProvider: DispatcherProvider
) : SettingsRepository {
    override val settings: Flow<AppSettings> = context.doclySettingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> preferences.toSettings() }

    override fun observeStorageUsage(): Flow<StorageUsage> = flow {
        emit(calculateStorageUsage())
    }.flowOn(dispatcherProvider.io)

    override suspend fun getSettings(): AppResult<AppSettings> = repositoryResult(dispatcherProvider) {
        settings.first()
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.THEME_MODE] = themeMode.name }

    override suspend fun updateDynamicColorEnabled(enabled: Boolean): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.DYNAMIC_COLOR_ENABLED] = enabled }

    override suspend fun updateDefaultScanMode(scanMode: ScanMode): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.DEFAULT_SCAN_MODE] = scanMode.name }

    override suspend fun updateAutoCaptureEnabled(enabled: Boolean): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.AUTO_CAPTURE_ENABLED] = enabled }

    override suspend fun updateReaderTextSizeSp(textSizeSp: Float): AppResult<Unit> = updatePreference { preferences ->
        preferences[Keys.READER_TEXT_SIZE_SP] = textSizeSp.coerceIn(
            AppSettings.MIN_READER_TEXT_SIZE_SP,
            AppSettings.MAX_READER_TEXT_SIZE_SP
        )
    }

    override suspend fun updateReaderThemeMode(readerThemeMode: ReaderThemeMode): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.READER_THEME_MODE] = readerThemeMode.name }

    override suspend fun updateDefaultPdfQuality(quality: PdfExportQuality): AppResult<Unit> =
        updatePreference { preferences -> preferences[Keys.DEFAULT_PDF_QUALITY] = quality.name }

    override suspend fun clearCache(): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        appFileDirectories.ensureDirectories()
        context.cacheDir.deleteChildren()
        appFileDirectories.tempDirectory.deleteChildren()
        cleanupRepository.cleanOrphanedFiles().throwOnError()
    }

    private suspend fun updatePreference(
        update: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ): AppResult<Unit> = withContext(dispatcherProvider.io) {
        runCatching {
            context.doclySettingsDataStore.edit { preferences -> update(preferences) }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { throwable ->
                AppResult.Error(
                    message = "Settings could not be saved.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        )
    }

    private fun Preferences.toSettings(): AppSettings = settingsFromPreferences(
        themeModeName = this[Keys.THEME_MODE],
        dynamicColorEnabled = this[Keys.DYNAMIC_COLOR_ENABLED],
        defaultScanModeName = this[Keys.DEFAULT_SCAN_MODE],
        autoCaptureEnabled = this[Keys.AUTO_CAPTURE_ENABLED],
        readerTextSizeSp = this[Keys.READER_TEXT_SIZE_SP],
        readerThemeModeName = this[Keys.READER_THEME_MODE],
        defaultPdfQualityName = this[Keys.DEFAULT_PDF_QUALITY]
    )

    private fun calculateStorageUsage(): StorageUsage {
        appFileDirectories.ensureDirectories()
        val documentsBytes = listOf(
            appFileDirectories.rawScanDirectory,
            appFileDirectories.processedScanDirectory,
            appFileDirectories.pdfDirectory,
            appFileDirectories.txtDirectory,
            appFileDirectories.markdownDirectory,
            appFileDirectories.htmlDirectory,
            appFileDirectories.docxDirectory,
            appFileDirectories.xlsxDirectory,
            appFileDirectories.csvDirectory,
            appFileDirectories.imageDirectory
        ).sumOf { directory -> directory.sizeBytes() }
        return StorageUsage(
            documentsBytes = documentsBytes,
            thumbnailsBytes = appFileDirectories.thumbnailDirectory.sizeBytes(),
            tempBytes = appFileDirectories.tempDirectory.sizeBytes(),
            exportsBytes = appFileDirectories.exportDirectory.sizeBytes(),
            cacheBytes = context.cacheDir.sizeBytes()
        )
    }

    private fun File.sizeBytes(): Long {
        if (!exists()) return 0L
        return walkTopDown()
            .filter { file -> file.isFile }
            .sumOf { file -> file.length() }
    }

    private fun File.deleteChildren() {
        if (!exists() || !isDirectory) return
        listFiles().orEmpty().forEach { child ->
            if (child.exists() && !child.deleteRecursively()) {
                throw RepositoryFailure(
                    message = "Could not clear cache file: ${child.absolutePath}",
                    category = AppErrorCategory.STORAGE
                )
            }
        }
    }

    private fun <T> AppResult<T>.throwOnError() {
        if (this is AppResult.Error) {
            throw RepositoryFailure(message = message, category = category, cause = throwable)
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val DEFAULT_SCAN_MODE = stringPreferencesKey("default_scan_mode")
        val AUTO_CAPTURE_ENABLED = booleanPreferencesKey("auto_capture_enabled")
        val READER_TEXT_SIZE_SP = floatPreferencesKey("reader_text_size_sp")
        val READER_THEME_MODE = stringPreferencesKey("reader_theme_mode")
        val DEFAULT_PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
    }
}
