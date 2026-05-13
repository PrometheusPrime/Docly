package com.docly.app.feature.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.ConversionOptions
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.usecase.converter.ConvertDocumentUseCase
import com.docly.app.domain.usecase.converter.GetSupportedConversionOutputsUseCase
import com.docly.app.domain.usecase.library.ObserveDocumentsUseCase
import com.docly.app.domain.usecase.reader.OpenXlsxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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
class ConverterViewModel @Inject constructor(
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val getSupportedConversionOutputsUseCase: GetSupportedConversionOutputsUseCase,
    private val convertDocumentUseCase: ConvertDocumentUseCase,
    private val openXlsxUseCase: OpenXlsxUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<ConverterUiEffect>()
    val uiEffect: SharedFlow<ConverterUiEffect> = _uiEffect.asSharedFlow()

    private var hasStarted = false
    private var observeJob: Job? = null
    private var sheetJob: Job? = null

    fun onEvent(event: ConverterUiEvent) {
        when (event) {
            ConverterUiEvent.OnStart -> start()
            is ConverterUiEvent.OnInputSelected -> selectInput(event.documentId)
            is ConverterUiEvent.OnOutputTypeSelected -> selectOutput(event.outputType)
            is ConverterUiEvent.OnOutputFileNameChanged -> updateOutputFileName(event.outputFileName)
            is ConverterUiEvent.OnXlsxSheetSelected -> selectXlsxSheet(event.sheetIndex)
            ConverterUiEvent.OnConvertClicked -> convert()
            ConverterUiEvent.OnOpenResultClicked -> openResult()
            ConverterUiEvent.OnShareResultClicked -> shareResult()
            ConverterUiEvent.OnViewDocumentsClicked -> viewDocuments()
        }
    }

    private fun start() {
        if (hasStarted) return
        hasStarted = true
        observeDocuments()
    }

    private fun observeDocuments() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = true, errorMessage = null) }
            observeDocumentsUseCase()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "We could not load documents. Please try again."
                        )
                    }
                }
                .collect { documents ->
                    val convertibleDocuments = documents
                        .filter { document -> document.supportedOutputs().isNotEmpty() }
                        .sortedByDescending { document -> document.updatedAt }
                    applySelection(convertibleDocuments = convertibleDocuments, isLoading = false)
                }
        }
    }

    private fun selectInput(documentId: String) {
        applySelection(
            convertibleDocuments = _uiState.value.documents,
            preferredDocumentId = documentId,
            preferredOutputType = null,
            isLoading = _uiState.value.isLoading
        )
    }

    private fun selectOutput(outputType: DocumentType) {
        applySelection(
            convertibleDocuments = _uiState.value.documents,
            preferredDocumentId = _uiState.value.selectedDocumentId,
            preferredOutputType = outputType,
            isLoading = _uiState.value.isLoading
        )
    }

    private fun updateOutputFileName(outputFileName: String) {
        _uiState.update { state ->
            state.copy(outputFileName = outputFileName, errorMessage = null, completedDocumentId = null)
        }
    }

    private fun selectXlsxSheet(sheetIndex: Int) {
        _uiState.update { state ->
            state.copy(selectedXlsxSheetIndex = sheetIndex, errorMessage = null, completedDocumentId = null)
        }
    }

    private fun applySelection(
        convertibleDocuments: List<DoclyDocument>,
        preferredDocumentId: String? = _uiState.value.selectedDocumentId,
        preferredOutputType: DocumentType? = _uiState.value.selectedOutputType,
        isLoading: Boolean
    ) {
        val currentState = _uiState.value
        val selectedDocument = convertibleDocuments.firstOrNull { document -> document.id == preferredDocumentId }
            ?: convertibleDocuments.firstOrNull()
        val supportedOutputs = selectedDocument?.supportedOutputs().orEmpty()
        val selectedOutput = preferredOutputType?.takeIf { output -> output in supportedOutputs }
            ?: supportedOutputs.firstOrNull()
        val selectionChanged = selectedDocument?.id != currentState.selectedDocumentId ||
            selectedOutput != currentState.selectedOutputType
        val outputFileName = if (selectionChanged) {
            selectedDocument?.defaultOutputFileName(selectedOutput).orEmpty()
        } else {
            currentState.outputFileName
        }

        _uiState.update { state ->
            state.copy(
                documents = convertibleDocuments,
                selectedDocumentId = selectedDocument?.id,
                selectedOutputType = selectedOutput,
                supportedOutputTypes = supportedOutputs,
                outputFileName = outputFileName,
                selectedXlsxSheetIndex = if (selectedDocument?.type == DocumentType.XLSX) {
                    state.selectedXlsxSheetIndex
                } else {
                    0
                },
                isLoading = isLoading,
                errorMessage = null,
                completedDocumentId = null,
                completedOutputPath = null,
                completedMimeType = null
            )
        }

        loadXlsxSheetsIfNeeded(selectedDocument)
    }

    private fun loadXlsxSheetsIfNeeded(selectedDocument: DoclyDocument?) {
        sheetJob?.cancel()
        if (selectedDocument?.type != DocumentType.XLSX) {
            _uiState.update { state -> state.copy(xlsxSheets = emptyList(), isLoadingSheets = false) }
            return
        }

        sheetJob = viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoadingSheets = true, errorMessage = null) }
            when (val result = openXlsxUseCase(selectedDocument.fileRef)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoadingSheets = false,
                        xlsxSheets = emptyList(),
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> _uiState.update { state ->
                    val sheets = result.data.sheets
                    val selectedSheet = sheets.firstOrNull { sheet -> sheet.index == state.selectedXlsxSheetIndex }
                        ?: sheets.firstOrNull()
                    state.copy(
                        isLoadingSheets = false,
                        xlsxSheets = sheets,
                        selectedXlsxSheetIndex = selectedSheet?.index ?: 0,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private fun convert() {
        val state = _uiState.value
        val selectedDocument = state.documents.firstOrNull { document -> document.id == state.selectedDocumentId }
        val selectedOutput = state.selectedOutputType
        if (selectedDocument == null || selectedOutput == null || state.outputFileName.isBlank()) {
            viewModelScope.launch {
                _uiEffect.emit(ConverterUiEffect.ShowToast("Choose a document and output format first."))
            }
            return
        }
        if (state.isConverting) return

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isConverting = true,
                    progress = RUNNING_PROGRESS,
                    errorMessage = null,
                    completedDocumentId = null,
                    completedOutputPath = null,
                    completedMimeType = null
                )
            }
            val request = ConversionRequest(
                inputDocumentId = selectedDocument.id,
                inputType = selectedDocument.type,
                outputType = selectedOutput,
                outputFileName = state.outputFileName,
                options = ConversionOptions(xlsxSheetIndex = state.selectedXlsxSheetIndex)
            )
            when (val result = convertDocumentUseCase(request)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { current ->
                        current.copy(isConverting = false, progress = 0, errorMessage = message)
                    }
                    _uiEffect.emit(ConverterUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    val outputDocument = result.data.outputDocument
                    _uiState.update { current ->
                        current.copy(
                            isConverting = false,
                            progress = result.data.job.progress,
                            completedDocumentId = outputDocument.id,
                            completedOutputPath = result.data.outputPath,
                            completedMimeType = outputDocument.mimeType,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(ConverterUiEffect.ShowToast("Document converted."))
                }
            }
        }
    }

    private fun openResult() {
        val documentId = _uiState.value.completedDocumentId
        viewModelScope.launch {
            if (documentId.isNullOrBlank()) {
                _uiEffect.emit(ConverterUiEffect.ShowToast("Convert a document before opening it."))
            } else {
                _uiEffect.emit(ConverterUiEffect.NavigateToReader(documentId))
            }
        }
    }

    private fun shareResult() {
        val state = _uiState.value
        viewModelScope.launch {
            val outputPath = state.completedOutputPath
            if (outputPath.isNullOrBlank()) {
                _uiEffect.emit(ConverterUiEffect.ShowToast("Convert a document before sharing it."))
            } else {
                _uiEffect.emit(
                    ConverterUiEffect.ShareDocument(
                        filePath = outputPath,
                        title = state.outputFileName,
                        mimeType = state.completedMimeType
                    )
                )
            }
        }
    }

    private fun viewDocuments() {
        viewModelScope.launch {
            _uiEffect.emit(ConverterUiEffect.NavigateToDocuments)
        }
    }

    private fun DoclyDocument.supportedOutputs(): List<DocumentType> = getSupportedConversionOutputsUseCase(type)

    private fun DoclyDocument.defaultOutputFileName(outputType: DocumentType?): String {
        val type = outputType ?: return ""
        return "${name.baseName()}.${type.defaultExtension()}"
    }

    private fun String.baseName(): String = trim()
        .replace(Regex("\\.(pdf|txt|md|markdown|html|htm|docx|xlsx|csv|jpe?g|png|webp)$", RegexOption.IGNORE_CASE), "")
        .ifBlank { "converted_document" }

    private fun DocumentType.defaultExtension(): String = when (this) {
        DocumentType.PDF -> "pdf"
        DocumentType.TXT -> "txt"
        DocumentType.MARKDOWN -> "md"
        DocumentType.HTML -> "html"
        DocumentType.CSV -> "csv"
        DocumentType.DOCX -> "docx"
        DocumentType.XLSX -> "xlsx"
        DocumentType.IMAGE -> "jpg"
        DocumentType.UNKNOWN -> "bin"
    }

    private companion object {
        const val RUNNING_PROGRESS = 10
    }
}
