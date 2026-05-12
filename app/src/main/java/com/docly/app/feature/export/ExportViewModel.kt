package com.docly.app.feature.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.usecase.export.ExportDocumentUseCase
import com.docly.app.domain.usecase.export.PrepareExportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prepareExportUseCase: PrepareExportUseCase,
    private val exportDocumentUseCase: ExportDocumentUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ExportUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ExportUiEffect>()
    val uiEffect: SharedFlow<ExportUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadExportPreview()
    }

    fun onEvent(event: ExportUiEvent) {
        when (event) {
            ExportUiEvent.OnLoad -> loadExportPreview()
            ExportUiEvent.OnExportClicked -> exportPdf()
            ExportUiEvent.OnOpenPdfClicked -> openPdf()
            ExportUiEvent.OnSharePdfClicked -> sharePdf()
            ExportUiEvent.OnOpenLibraryClicked -> navigateToLibrary()
        }
    }

    private fun loadExportPreview() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isExportReady = false,
                    errorMessage = "Scan session not found."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isExportReady = false,
                    isExporting = false,
                    errorMessage = null
                )
            }

            when (val result = prepareExportUseCase(sessionId)) {
                is AppResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isExportReady = false,
                            errorMessage = result.toUserMessage()
                        )
                    }
                }

                is AppResult.Success -> {
                    val preparedExport = result.data
                    _uiState.update { state ->
                        state.copy(
                            fileName = preparedExport.fileName,
                            title = preparedExport.fileName.removeSuffix(PDF_EXTENSION),
                            metadataSummary = preparedExport.metadata.summaryText(),
                            pageCount = preparedExport.pages.size,
                            isLoading = false,
                            isExportReady = true,
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    private fun exportPdf() {
        val state = _uiState.value
        if (!state.canExport) {
            viewModelScope.launch {
                _uiEffect.emit(ExportUiEffect.ShowToast(state.errorMessage ?: "PDF is not ready to export."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isExporting = true, errorMessage = null)
            }

            when (val result = exportDocumentUseCase(state.sessionId)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isExporting = false, errorMessage = message)
                    }
                    _uiEffect.emit(ExportUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    val document = result.data.document
                    val pdfPath = (document.fileRef as? FileRef.InternalFile)?.path.orEmpty()
                    _uiState.update { currentState ->
                        currentState.copy(
                            fileName = document.name + PDF_EXTENSION,
                            title = document.name,
                            metadataSummary = "Scanned document",
                            pageCount = document.pageCount ?: 0,
                            isExporting = false,
                            isExportReady = false,
                            exportedDocumentId = document.id,
                            exportedPdfPath = pdfPath,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ExportUiEffect.ShowToast("PDF exported."))
                }
            }
        }
    }

    private fun openPdf() {
        val pdfPath = _uiState.value.exportedPdfPath
        viewModelScope.launch {
            if (pdfPath.isNullOrBlank()) {
                _uiEffect.emit(ExportUiEffect.ShowToast("Export the PDF before opening it."))
            } else {
                _uiEffect.emit(ExportUiEffect.OpenPdf(pdfPath))
            }
        }
    }

    private fun sharePdf() {
        val state = _uiState.value
        viewModelScope.launch {
            val pdfPath = state.exportedPdfPath
            if (pdfPath.isNullOrBlank()) {
                _uiEffect.emit(ExportUiEffect.ShowToast("Export the PDF before sharing it."))
            } else {
                _uiEffect.emit(
                    ExportUiEffect.SharePdf(
                        pdfPath = pdfPath,
                        title = state.title.ifBlank {
                            state.fileName
                        }
                    )
                )
            }
        }
    }

    private fun navigateToLibrary() {
        viewModelScope.launch {
            _uiEffect.emit(ExportUiEffect.NavigateToLibrary)
        }
    }

    private fun DocumentMetadata.summaryText(): String {
        val paperNumberText = paperNumber?.takeIf { it.isNotBlank() }?.let { paperNumber -> " $paperNumber" }.orEmpty()
        return "$grade - $subject - $year - $paperType$paperNumberText"
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
        const val PDF_EXTENSION = ".pdf"
    }
}
