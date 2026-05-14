package com.docly.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettingsUseCase: ObserveSettingsUseCase,
    private val observeStorageUsageUseCase: ObserveStorageUsageUseCase,
    private val updateThemeModeUseCase: UpdateThemeModeUseCase,
    private val updateDynamicColorUseCase: UpdateDynamicColorUseCase,
    private val updateDefaultScanModeUseCase: UpdateDefaultScanModeUseCase,
    private val updateAutoCaptureUseCase: UpdateAutoCaptureUseCase,
    private val updateReaderTextSizeUseCase: UpdateReaderTextSizeUseCase,
    private val updateReaderThemeUseCase: UpdateReaderThemeUseCase,
    private val updateDefaultPdfQualityUseCase: UpdateDefaultPdfQualityUseCase,
    private val clearCacheUseCase: ClearCacheUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<SettingsUiEffect>()
    val uiEffect: SharedFlow<SettingsUiEffect> = _uiEffect.asSharedFlow()

    init {
        observeSettings()
        refreshStorageUsage()
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnThemeModeSelected -> updateSetting {
                updateThemeModeUseCase(event.themeMode)
            }

            is SettingsUiEvent.OnDynamicColorChanged -> updateSetting {
                updateDynamicColorUseCase(event.enabled)
            }

            is SettingsUiEvent.OnDefaultScanModeSelected -> updateSetting {
                updateDefaultScanModeUseCase(event.scanMode)
            }

            is SettingsUiEvent.OnAutoCaptureChanged -> updateSetting {
                updateAutoCaptureUseCase(event.enabled)
            }

            is SettingsUiEvent.OnReaderTextSizeChanged -> updateReaderTextSize(event.textSizeSp)

            is SettingsUiEvent.OnReaderThemeSelected -> updateSetting {
                updateReaderThemeUseCase(event.readerThemeMode)
            }

            is SettingsUiEvent.OnDefaultPdfQualitySelected -> updateSetting {
                updateDefaultPdfQualityUseCase(event.quality)
            }

            SettingsUiEvent.OnRefreshStorageClicked -> refreshStorageUsage()

            SettingsUiEvent.OnClearCacheClicked -> clearCache()
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            observeSettingsUseCase()
                .catch { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Settings could not be loaded."
                        )
                    }
                }
                .collect { settings ->
                    _uiState.update { state ->
                        state.copy(
                            settings = settings,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun updateReaderTextSize(textSizeSp: Float) {
        val safeSize = textSizeSp.coerceIn(
            AppSettings.MIN_READER_TEXT_SIZE_SP,
            AppSettings.MAX_READER_TEXT_SIZE_SP
        )
        updateSetting { updateReaderTextSizeUseCase(safeSize) }
    }

    private fun refreshStorageUsage() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isStorageLoading = true, errorMessage = null) }
            observeStorageUsageUseCase()
                .catch { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isStorageLoading = false,
                            errorMessage = throwable.message ?: "Storage usage could not be loaded."
                        )
                    }
                }
                .collect { usage ->
                    _uiState.update { state ->
                        state.copy(storageUsage = usage, isStorageLoading = false, errorMessage = null)
                    }
                }
        }
    }

    private fun clearCache() {
        if (_uiState.value.isClearingCache) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isClearingCache = true, errorMessage = null) }
            when (val result = clearCacheUseCase()) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state -> state.copy(isClearingCache = false, errorMessage = message) }
                    _uiEffect.emit(SettingsUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state -> state.copy(isClearingCache = false, errorMessage = null) }
                    _uiEffect.emit(SettingsUiEffect.ShowToast("Cache cleared."))
                    refreshStorageUsage()
                }
            }
        }
    }

    private fun updateSetting(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state -> state.copy(errorMessage = message) }
                    _uiEffect.emit(SettingsUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state -> state.copy(errorMessage = null) }
                }
            }
        }
    }
}
