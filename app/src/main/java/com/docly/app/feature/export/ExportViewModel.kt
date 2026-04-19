package com.docly.app.feature.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.export.SaveDocumentUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val generateDocumentNameUseCase: GenerateDocumentNameUseCase,
    private val generatePdfUseCase: GeneratePdfUseCase,
    private val saveDocumentUseCase: SaveDocumentUseCase,
    private val updateScanSessionStatusUseCase: UpdateScanSessionStatusUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ExportUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
