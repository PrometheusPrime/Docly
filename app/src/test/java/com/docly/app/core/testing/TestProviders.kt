package com.docly.app.core.testing

import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.DiagnosticEvent
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.StorageUsage
import com.docly.app.domain.model.ThemeMode
import com.docly.app.domain.repository.DiagnosticsRepository
import com.docly.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FixedIdProvider(private val id: String) : IdProvider {
    override fun generateId(): String = id
}

class SequenceIdProvider(ids: List<String>) : IdProvider {
    private val iterator = ids.iterator()

    override fun generateId(): String {
        check(iterator.hasNext()) { "No more test IDs are available." }
        return iterator.next()
    }
}

class FixedTimeProvider(private val timestampMillis: Long) : TimeProvider {
    override fun now(): Long = timestampMillis
}

class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

class NoOpAppLogger : AppLogger {
    override fun debug(tag: String, message: String) = Unit

    override fun warning(tag: String, message: String, throwable: Throwable?) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

class NoOpDiagnosticsRepository : DiagnosticsRepository {
    val events = mutableListOf<DiagnosticEvent>()

    override suspend fun record(event: DiagnosticEvent): AppResult<Unit> {
        events += event
        return AppResult.Success(Unit)
    }

    override fun observeRecent(limit: Int): Flow<List<DiagnosticEvent>> = flowOf(events.take(limit))
}

class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    initialStorageUsage: StorageUsage = StorageUsage()
) : SettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)
    private val storageUsageFlow = MutableStateFlow(initialStorageUsage)
    var clearCacheCalls = 0

    override val settings: Flow<AppSettings> = settingsFlow

    override fun observeStorageUsage(): Flow<StorageUsage> = storageUsageFlow

    override suspend fun getSettings(): AppResult<AppSettings> = AppResult.Success(settingsFlow.value)

    override suspend fun updateThemeMode(themeMode: ThemeMode): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(themeMode = themeMode)
        return AppResult.Success(Unit)
    }

    override suspend fun updateDynamicColorEnabled(enabled: Boolean): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(dynamicColorEnabled = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun updateDefaultScanMode(scanMode: ScanMode): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(defaultScanMode = scanMode)
        return AppResult.Success(Unit)
    }

    override suspend fun updateAutoCaptureEnabled(enabled: Boolean): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(autoCaptureEnabled = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun updateReaderTextSizeSp(textSizeSp: Float): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(readerTextSizeSp = textSizeSp)
        return AppResult.Success(Unit)
    }

    override suspend fun updateReaderThemeMode(readerThemeMode: ReaderThemeMode): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(readerThemeMode = readerThemeMode)
        return AppResult.Success(Unit)
    }

    override suspend fun updateDefaultPdfQuality(quality: PdfExportQuality): AppResult<Unit> {
        settingsFlow.value = settingsFlow.value.copy(defaultPdfQuality = quality)
        return AppResult.Success(Unit)
    }

    override suspend fun clearCache(): AppResult<Unit> {
        clearCacheCalls += 1
        storageUsageFlow.value = storageUsageFlow.value.copy(cacheBytes = 0L, tempBytes = 0L)
        return AppResult.Success(Unit)
    }
}
