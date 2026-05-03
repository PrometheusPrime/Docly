package com.docly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveSavedDocumentsUseCase
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
class LibraryViewModel @Inject constructor(
    private val observeSavedDocumentsUseCase: ObserveSavedDocumentsUseCase,
    private val deleteSavedDocumentUseCase: DeleteSavedDocumentUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LibraryUiEffect>()
    val uiEffect: SharedFlow<LibraryUiEffect> = _uiEffect.asSharedFlow()

    private var allDocuments: List<SavedDocument> = emptyList()
    private var observeJob: Job? = null

    init {
        observeDocuments()
    }

    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            LibraryUiEvent.OnLoad -> observeDocuments()
            is LibraryUiEvent.OnSearchQueryChanged -> updateSearchQuery(event.query)
            LibraryUiEvent.OnClearSearchClicked -> updateSearchQuery("")
            is LibraryUiEvent.OnOpenDocumentClicked -> openDocument(event.documentId)
            is LibraryUiEvent.OnShareDocumentClicked -> shareDocument(event.documentId)
            is LibraryUiEvent.OnDeleteDocumentClicked -> selectDocumentForDelete(event.documentId)
            LibraryUiEvent.OnDeleteDocumentConfirmed -> deletePendingDocument()
            LibraryUiEvent.OnDeleteDocumentDismissed -> dismissDeleteConfirmation()
        }
    }

    private fun observeDocuments() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            observeSavedDocumentsUseCase()
                .catch {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = "We could not load saved documents. Please try again."
                        )
                    }
                }
                .collect { documents ->
                    allDocuments = documents
                    _uiState.update { state ->
                        state.copy(
                            documents = documents.filteredBy(state.searchQuery),
                            totalDocumentCount = documents.size,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                documents = allDocuments.filteredBy(query),
                totalDocumentCount = allDocuments.size,
                searchQuery = query,
                errorMessage = null
            )
        }
    }

    private fun openDocument(documentId: String) {
        val document = allDocuments.findById(documentId)
        viewModelScope.launch {
            if (document == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Saved document not found."))
            } else {
                _uiEffect.emit(LibraryUiEffect.OpenPdf(document.pdfPath))
            }
        }
    }

    private fun shareDocument(documentId: String) {
        val document = allDocuments.findById(documentId)
        viewModelScope.launch {
            if (document == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Saved document not found."))
            } else {
                _uiEffect.emit(
                    LibraryUiEffect.SharePdf(
                        pdfPath = document.pdfPath,
                        title = document.title
                    )
                )
            }
        }
    }

    private fun selectDocumentForDelete(documentId: String) {
        val document = allDocuments.findById(documentId)
        if (document == null) {
            viewModelScope.launch {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Saved document not found."))
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                pendingDeleteDocument = document,
                errorMessage = null
            )
        }
    }

    private fun dismissDeleteConfirmation() {
        if (_uiState.value.isDeleting) return

        _uiState.update { state ->
            state.copy(pendingDeleteDocument = null)
        }
    }

    private fun deletePendingDocument() {
        val document = _uiState.value.pendingDeleteDocument ?: return
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isDeleting = true, errorMessage = null)
            }

            when (val result = deleteSavedDocumentUseCase(document.id)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(
                            isDeleting = false,
                            pendingDeleteDocument = null,
                            errorMessage = message
                        )
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isDeleting = false,
                            pendingDeleteDocument = null,
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast("Document deleted."))
                }
            }
        }
    }

    private fun List<SavedDocument>.findById(documentId: String): SavedDocument? =
        firstOrNull { document -> document.id == documentId }

    private fun List<SavedDocument>.filteredBy(query: String): List<SavedDocument> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return this

        return filter { document -> document.matches(normalizedQuery) }
    }

    private fun SavedDocument.matches(query: String): Boolean {
        val searchableText = buildString {
            append(title)
            append(' ')
            append(metadata.grade)
            append(' ')
            append(metadata.subject)
            append(' ')
            append(metadata.year)
            append(' ')
            append(metadata.paperType)
            metadata.paperNumber?.let { paperNumber ->
                append(' ')
                append(paperNumber)
            }
        }
        return searchableText.contains(query, ignoreCase = true)
    }
}
