package com.docly.app.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.usecase.create.CreateDocumentUseCase
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
class CreateViewModel @Inject constructor(private val createDocumentUseCase: CreateDocumentUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateUiState())
    val uiState: StateFlow<CreateUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<CreateUiEffect>()
    val uiEffect: SharedFlow<CreateUiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: CreateUiEvent) {
        when (event) {
            is CreateUiEvent.OnTitleChanged -> _uiState.update { state ->
                state.copy(title = event.title, errorMessage = null)
            }

            is CreateUiEvent.OnTypeSelected -> {
                if (event.type in CREATABLE_TYPES) {
                    _uiState.update { state -> state.copy(selectedType = event.type, errorMessage = null) }
                }
            }

            CreateUiEvent.OnCreateClicked -> createDocument()

            CreateUiEvent.OnCreatePdfFromScanClicked -> navigateToScanner()
        }
    }

    private fun createDocument() {
        val state = _uiState.value
        if (!state.canCreate) {
            val message = if (state.title.isBlank()) {
                "Document title is required."
            } else {
                "This document type cannot be created yet."
            }
            _uiState.update { currentState -> currentState.copy(errorMessage = message) }
            viewModelScope.launch {
                _uiEffect.emit(CreateUiEffect.ShowToast(message))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState -> currentState.copy(isCreating = true, errorMessage = null) }
            when (val result = createDocumentUseCase(title = state.title, type = state.selectedType)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { currentState ->
                        currentState.copy(isCreating = false, errorMessage = message)
                    }
                    _uiEffect.emit(CreateUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(isCreating = false, errorMessage = null)
                    }
                    _uiEffect.emit(CreateUiEffect.ShowToast("${state.selectedType.label()} created."))
                    _uiEffect.emit(CreateUiEffect.NavigateToEditor(result.data.id))
                }
            }
        }
    }

    private fun navigateToScanner() {
        viewModelScope.launch {
            _uiEffect.emit(CreateUiEffect.NavigateToScanner)
        }
    }

    private fun DocumentType.label(): String = when (this) {
        DocumentType.TXT -> "TXT document"
        DocumentType.MARKDOWN -> "Markdown document"
        DocumentType.HTML -> "HTML document"
        else -> "Document"
    }
}
