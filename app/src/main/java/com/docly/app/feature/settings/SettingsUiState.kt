package com.docly.app.feature.settings

import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.StorageUsage

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val storageUsage: StorageUsage = StorageUsage(),
    val isLoading: Boolean = true,
    val isStorageLoading: Boolean = true,
    val isClearingCache: Boolean = false,
    val errorMessage: String? = null
)
