package com.docly.app.feature.metadata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanMetadataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class MetadataViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val updateScanMetadataUseCase: UpdateScanMetadataUseCase,
    private val generateDocumentNameUseCase: GenerateDocumentNameUseCase,
    private val validateMetadataUseCase: ValidateMetadataUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MetadataUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<MetadataUiState> = _uiState.asStateFlow()

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
