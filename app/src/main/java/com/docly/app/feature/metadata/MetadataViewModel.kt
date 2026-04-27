package com.docly.app.feature.metadata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanMetadataUseCase
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

    private val _uiEffect = MutableSharedFlow<MetadataUiEffect>()
    val uiEffect: SharedFlow<MetadataUiEffect> = _uiEffect.asSharedFlow()

    init {
        loadSession()
    }

    fun onEvent(event: MetadataUiEvent) {
        when (event) {
            MetadataUiEvent.OnLoad -> loadSession()
            is MetadataUiEvent.OnGradeChanged -> updateForm { state -> state.copy(grade = event.value) }
            is MetadataUiEvent.OnSubjectChanged -> updateForm { state -> state.copy(subject = event.value) }
            is MetadataUiEvent.OnYearChanged -> updateForm { state -> state.copy(year = event.value) }
            is MetadataUiEvent.OnPaperTypeChanged -> updateForm { state -> state.copy(paperType = event.value) }
            is MetadataUiEvent.OnPaperNumberChanged -> updateForm { state -> state.copy(paperNumber = event.value) }
            is MetadataUiEvent.OnSourceChanged -> updateForm { state -> state.copy(source = event.value) }
            is MetadataUiEvent.OnNotesChanged -> updateForm { state -> state.copy(notes = event.value) }
            MetadataUiEvent.OnContinueClicked -> saveMetadata()
        }
    }

    private fun loadSession() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSaving = false,
                    isSessionAvailable = false,
                    errorMessage = "Scan session not found."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isSaving = false,
                    isSessionAvailable = false,
                    errorMessage = null,
                    validationErrors = emptyList()
                )
            }

            when (val result = getScanSessionUseCase(sessionId)) {
                is AppResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isSaving = false,
                            isSessionAvailable = false,
                            errorMessage = result.toUserMessage()
                        )
                    }
                }

                is AppResult.Success -> {
                    val metadata = result.data?.metadata
                    val loadedState = _uiState.value.copy(
                        grade = metadata?.grade.orEmpty(),
                        subject = metadata?.subject.orEmpty(),
                        year = metadata?.year?.toString().orEmpty(),
                        paperType = metadata?.paperType.orEmpty(),
                        paperNumber = metadata?.paperNumber.orEmpty(),
                        source = metadata?.source.orEmpty(),
                        notes = metadata?.notes.orEmpty(),
                        validationErrors = emptyList(),
                        isLoading = false,
                        isSaving = false,
                        isSessionAvailable = result.data != null,
                        errorMessage = if (result.data == null) "Scan session not found." else null
                    )
                    _uiState.value = loadedState.copy(generatedFileName = loadedState.previewFileName())
                }
            }
        }
    }

    private fun updateForm(update: (MetadataUiState) -> MetadataUiState) {
        _uiState.update { state ->
            val updatedState = update(state).copy(validationErrors = emptyList(), errorMessage = null)
            updatedState.copy(generatedFileName = updatedState.previewFileName())
        }
    }

    private fun saveMetadata() {
        val state = _uiState.value
        if (!state.canContinue) {
            if (state.sessionId.isBlank()) {
                _uiState.update { currentState -> currentState.copy(errorMessage = "Scan session not found.") }
            }
            return
        }

        val validation = state.validateInput()
        if (validation.errors.isNotEmpty() || validation.metadata == null) {
            _uiState.update { currentState ->
                currentState.copy(validationErrors = validation.errors, errorMessage = null)
            }
            return
        }

        val metadata = validation.metadata
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    grade = metadata.grade,
                    subject = metadata.subject,
                    year = metadata.year.toString(),
                    paperType = metadata.paperType,
                    paperNumber = metadata.paperNumber.orEmpty(),
                    source = metadata.source.orEmpty(),
                    notes = metadata.notes.orEmpty(),
                    generatedFileName = generateDocumentNameUseCase(metadata),
                    validationErrors = emptyList(),
                    errorMessage = null,
                    isSaving = true
                )
            }

            when (val result = updateScanMetadataUseCase(state.sessionId, metadata)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isSaving = false, errorMessage = message)
                    }
                    _uiEffect.emit(MetadataUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(isSaving = false, errorMessage = null, validationErrors = emptyList())
                    }
                    _uiEffect.emit(MetadataUiEffect.NavigateToExport(state.sessionId))
                }
            }
        }
    }

    private fun MetadataUiState.previewFileName(): String {
        val metadata = toMetadataOrNull() ?: return ""
        return generateDocumentNameUseCase(metadata)
    }

    private fun MetadataUiState.validateInput(): MetadataInputValidation {
        val yearValue = year.trim().toIntOrNull()
        if (yearValue == null) {
            val requiredErrors = buildList {
                if (grade.isBlank()) add("Grade is required.")
                if (subject.isBlank()) add("Subject is required.")
                if (paperType.isBlank()) add("Paper type is required.")
                add("Year must be a whole number.")
            }
            return MetadataInputValidation(metadata = null, errors = requiredErrors)
        }

        val metadata = toMetadata(yearValue)
        return MetadataInputValidation(
            metadata = metadata,
            errors = validateMetadataUseCase(metadata).errors
        )
    }

    private fun MetadataUiState.toMetadataOrNull(): DocumentMetadata? {
        val yearValue = year.trim().toIntOrNull() ?: return null
        if (grade.isBlank() || subject.isBlank() || paperType.isBlank()) return null
        return toMetadata(yearValue)
    }

    private fun MetadataUiState.toMetadata(yearValue: Int): DocumentMetadata = DocumentMetadata(
        grade = grade.trim(),
        subject = subject.trim(),
        year = yearValue,
        paperType = paperType.trim(),
        paperNumber = paperNumber.trim().ifBlank { null },
        source = source.trim().ifBlank { null },
        notes = notes.trim().ifBlank { null }
    )

    private data class MetadataInputValidation(val metadata: DocumentMetadata?, val errors: List<String>)

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
