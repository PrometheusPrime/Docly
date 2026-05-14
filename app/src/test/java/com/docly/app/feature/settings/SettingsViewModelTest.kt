package com.docly.app.feature.settings

import com.docly.app.core.testing.FakeSettingsRepository
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.StorageUsage
import com.docly.app.domain.model.ThemeMode
import com.docly.app.domain.usecase.settings.ClearCacheUseCase
import com.docly.app.domain.usecase.settings.ObserveSettingsUseCase
import com.docly.app.domain.usecase.settings.ObserveStorageUsageUseCase
import com.docly.app.domain.usecase.settings.UpdateAutoCaptureUseCase
import com.docly.app.domain.usecase.settings.UpdateDefaultPdfQualityUseCase
import com.docly.app.domain.usecase.settings.UpdateDefaultScanModeUseCase
import com.docly.app.domain.usecase.settings.UpdateDynamicColorUseCase
import com.docly.app.domain.usecase.settings.UpdateReaderTextSizeUseCase
import com.docly.app.domain.usecase.settings.UpdateReaderThemeUseCase
import com.docly.app.domain.usecase.settings.UpdateThemeModeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun updatesPersistedSettingsAndState() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(SettingsUiEvent.OnThemeModeSelected(ThemeMode.DARK))
        viewModel.onEvent(SettingsUiEvent.OnDefaultScanModeSelected(ScanMode.COLOR))
        viewModel.onEvent(SettingsUiEvent.OnAutoCaptureChanged(true))
        viewModel.onEvent(SettingsUiEvent.OnDefaultPdfQualitySelected(PdfExportQuality.MEDIUM))
        advanceUntilIdle()

        val settings = viewModel.uiState.value.settings
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(ScanMode.COLOR, settings.defaultScanMode)
        assertEquals(true, settings.autoCaptureEnabled)
        assertEquals(PdfExportQuality.MEDIUM, settings.defaultPdfQuality)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun clearCacheRefreshesStorageUsage() = runTest {
        val repository = FakeSettingsRepository(
            initialStorageUsage = StorageUsage(tempBytes = 100L, cacheBytes = 200L)
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(SettingsUiEvent.OnClearCacheClicked)
        advanceUntilIdle()

        assertEquals(1, repository.clearCacheCalls)
        assertEquals(0L, viewModel.uiState.value.storageUsage.tempBytes)
        assertEquals(0L, viewModel.uiState.value.storageUsage.cacheBytes)
    }

    private fun viewModel(repository: FakeSettingsRepository): SettingsViewModel = SettingsViewModel(
        observeSettingsUseCase = ObserveSettingsUseCase(repository),
        observeStorageUsageUseCase = ObserveStorageUsageUseCase(repository),
        updateThemeModeUseCase = UpdateThemeModeUseCase(repository),
        updateDynamicColorUseCase = UpdateDynamicColorUseCase(repository),
        updateDefaultScanModeUseCase = UpdateDefaultScanModeUseCase(repository),
        updateAutoCaptureUseCase = UpdateAutoCaptureUseCase(repository),
        updateReaderTextSizeUseCase = UpdateReaderTextSizeUseCase(repository),
        updateReaderThemeUseCase = UpdateReaderThemeUseCase(repository),
        updateDefaultPdfQualityUseCase = UpdateDefaultPdfQualityUseCase(repository),
        clearCacheUseCase = ClearCacheUseCase(repository)
    )

    class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
