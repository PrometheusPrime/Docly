package com.docly.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.SortMode
import com.docly.app.domain.model.ViewMode
import com.docly.app.domain.usecase.library.DeleteDocumentUseCase
import com.docly.app.domain.usecase.library.ImportDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveDocumentsUseCase
import com.docly.app.domain.usecase.library.RenameDocumentUseCase
import com.docly.app.domain.usecase.library.SearchDocumentsUseCase
import com.docly.app.domain.usecase.library.ToggleFavoriteDocumentUseCase
import com.docly.app.domain.usecase.library.UpdateLastOpenedUseCase
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
    private val observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val searchDocumentsUseCase: SearchDocumentsUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val toggleFavoriteDocumentUseCase: ToggleFavoriteDocumentUseCase,
    private val updateLastOpenedUseCase: UpdateLastOpenedUseCase,
    private val capabilityResolver: DocumentCapabilityResolver
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LibraryUiEffect>()
    val uiEffect: SharedFlow<LibraryUiEffect> = _uiEffect.asSharedFlow()

    private var allDocuments: List<DoclyDocument> = emptyList()
    private var observeJob: Job? = null
    private var searchJob: Job? = null

    init {
        observeDocuments()
    }

    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            LibraryUiEvent.OnLoad -> observeDocuments()
            LibraryUiEvent.OnImportDocumentClicked -> launchImport()
            is LibraryUiEvent.OnImportDocumentSelected -> importDocument(event.uriString)
            is LibraryUiEvent.OnSearchQueryChanged -> updateSearchQuery(event.query)
            LibraryUiEvent.OnClearSearchClicked -> updateSearchQuery("")
            is LibraryUiEvent.OnSortModeChanged -> updateSortMode(event.sortMode)
            is LibraryUiEvent.OnTypeFilterChanged -> updateTypeFilter(event.documentType)
            LibraryUiEvent.OnFavoriteFilterToggled -> toggleFavoriteFilter()
            is LibraryUiEvent.OnViewModeChanged -> updateViewMode(event.viewMode)
            is LibraryUiEvent.OnOpenDocumentClicked -> openDocument(event.documentId)
            is LibraryUiEvent.OnShareDocumentClicked -> shareDocument(event.documentId)
            is LibraryUiEvent.OnFavoriteDocumentClicked -> toggleFavorite(event.documentId)
            is LibraryUiEvent.OnRenameDocumentClicked -> selectDocumentForRename(event.documentId)
            is LibraryUiEvent.OnRenameDocumentNameChanged -> updatePendingRenameName(event.name)
            LibraryUiEvent.OnRenameDocumentConfirmed -> renamePendingDocument()
            LibraryUiEvent.OnRenameDocumentDismissed -> dismissRenameConfirmation()
            is LibraryUiEvent.OnDeleteDocumentClicked -> selectDocumentForDelete(event.documentId)
            LibraryUiEvent.OnDeleteDocumentConfirmed -> deletePendingDocument()
            LibraryUiEvent.OnDeleteDocumentDismissed -> dismissDeleteConfirmation()
        }
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
                    allDocuments = documents
                    _uiState.update { state ->
                        state.copy(
                            documents = documents.applyUiFilters(state),
                            totalDocumentCount = documents.size,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun launchImport() {
        viewModelScope.launch {
            _uiEffect.emit(LibraryUiEffect.LaunchImportPicker)
        }
    }

    private fun importDocument(uriString: String) {
        if (uriString.isBlank()) return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isImporting = true, errorMessage = null) }
            when (val result = importDocumentUseCase(uriString)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state -> state.copy(isImporting = false, errorMessage = message) }
                    _uiEffect.emit(LibraryUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state -> state.copy(isImporting = false, errorMessage = null) }
                    _uiEffect.emit(LibraryUiEffect.ShowToast("Document imported."))
                }
            }
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                documents = if (query.isBlank()) {
                    allDocuments.applyUiFilters(
                        state.copy(searchQuery = "")
                    )
                } else {
                    state.documents
                },
                errorMessage = null
            )
        }
        observeSearch(query)
    }

    private fun observeSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            applyCurrentFilters()
            return
        }

        searchJob = viewModelScope.launch {
            searchDocumentsUseCase(query)
                .catch {
                    _uiState.update { state ->
                        state.copy(errorMessage = "We could not search documents. Please try again.")
                    }
                }
                .collect { documents ->
                    _uiState.update { state ->
                        state.copy(
                            documents = documents.applyUiFilters(state),
                            totalDocumentCount = allDocuments.size,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun updateSortMode(sortMode: SortMode) {
        _uiState.update { state -> state.copy(sortMode = sortMode) }
        applyCurrentFilters()
    }

    private fun updateTypeFilter(documentType: DocumentType?) {
        _uiState.update { state -> state.copy(typeFilter = documentType) }
        applyCurrentFilters()
    }

    private fun toggleFavoriteFilter() {
        _uiState.update { state -> state.copy(favoritesOnly = !state.favoritesOnly) }
        applyCurrentFilters()
    }

    private fun updateViewMode(viewMode: ViewMode) {
        _uiState.update { state -> state.copy(viewMode = viewMode) }
    }

    private fun openDocument(documentId: String) {
        val document = allDocuments.findById(documentId)
        viewModelScope.launch {
            if (document == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Document not found."))
                return@launch
            }
            if (!capabilityResolver.resolve(document.type).canView) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Docly cannot open this file type yet."))
                return@launch
            }
            val path = document.internalPathOrNull()
            if (path == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Document file not found."))
                return@launch
            }
            updateLastOpenedUseCase(document.id)
            _uiEffect.emit(LibraryUiEffect.OpenDocument(filePath = path, mimeType = document.mimeType))
        }
    }

    private fun shareDocument(documentId: String) {
        val document = allDocuments.findById(documentId)
        viewModelScope.launch {
            if (document == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Document not found."))
                return@launch
            }
            val path = document.internalPathOrNull()
            if (path == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Document file not found."))
                return@launch
            }
            _uiEffect.emit(
                LibraryUiEffect.ShareDocument(
                    filePath = path,
                    title = document.name,
                    mimeType = document.mimeType
                )
            )
        }
    }

    private fun toggleFavorite(documentId: String) {
        val document = allDocuments.findById(documentId)
        viewModelScope.launch {
            if (document == null) {
                _uiEffect.emit(LibraryUiEffect.ShowToast("Document not found."))
                return@launch
            }
            when (val result = toggleFavoriteDocumentUseCase(document.id, !document.isFavorite)) {
                is AppResult.Error -> _uiEffect.emit(LibraryUiEffect.ShowToast(result.toUserMessage()))
                is AppResult.Success -> Unit
            }
        }
    }

    private fun selectDocumentForRename(documentId: String) {
        val document = allDocuments.findById(documentId)
        if (document == null) {
            viewModelScope.launch { _uiEffect.emit(LibraryUiEffect.ShowToast("Document not found.")) }
            return
        }
        _uiState.update { state ->
            state.copy(
                pendingRenameDocument = document,
                pendingRenameName = document.name,
                errorMessage = null
            )
        }
    }

    private fun updatePendingRenameName(name: String) {
        _uiState.update { state -> state.copy(pendingRenameName = name) }
    }

    private fun dismissRenameConfirmation() {
        if (_uiState.value.isRenaming) return
        _uiState.update { state -> state.copy(pendingRenameDocument = null, pendingRenameName = "") }
    }

    private fun renamePendingDocument() {
        val state = _uiState.value
        val document = state.pendingRenameDocument ?: return
        if (state.isRenaming) return

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isRenaming = true, errorMessage = null) }
            when (val result = renameDocumentUseCase(document.id, state.pendingRenameName)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { current ->
                        current.copy(isRenaming = false, pendingRenameDocument = null, errorMessage = message)
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isRenaming = false,
                            pendingRenameDocument = null,
                            pendingRenameName = "",
                            errorMessage = null
                        )
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast("Document renamed."))
                }
            }
        }
    }

    private fun selectDocumentForDelete(documentId: String) {
        val document = allDocuments.findById(documentId)
        if (document == null) {
            viewModelScope.launch { _uiEffect.emit(LibraryUiEffect.ShowToast("Document not found.")) }
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
        _uiState.update { state -> state.copy(pendingDeleteDocument = null) }
    }

    private fun deletePendingDocument() {
        val document = _uiState.value.pendingDeleteDocument ?: return
        if (_uiState.value.isDeleting) return

        viewModelScope.launch {
            _uiState.update { state -> state.copy(isDeleting = true, errorMessage = null) }

            when (val result = deleteDocumentUseCase(document.id)) {
                is AppResult.Error -> {
                    val message = result.toUserMessage()
                    _uiState.update { state ->
                        state.copy(isDeleting = false, pendingDeleteDocument = null, errorMessage = message)
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast(message))
                }

                is AppResult.Success -> {
                    _uiState.update { state ->
                        state.copy(isDeleting = false, pendingDeleteDocument = null, errorMessage = null)
                    }
                    _uiEffect.emit(LibraryUiEffect.ShowToast("Document deleted."))
                }
            }
        }
    }

    private fun applyCurrentFilters() {
        _uiState.update { state ->
            state.copy(documents = allDocuments.applyUiFilters(state), totalDocumentCount = allDocuments.size)
        }
    }

    private fun List<DoclyDocument>.applyUiFilters(state: LibraryUiState): List<DoclyDocument> = asSequence()
        .filter { document -> state.typeFilter == null || document.type == state.typeFilter }
        .filter { document -> !state.favoritesOnly || document.isFavorite }
        .sortedWith(state.sortMode.comparator())
        .toList()

    private fun SortMode.comparator(): Comparator<DoclyDocument> = when (this) {
        SortMode.UPDATED_DESC -> compareByDescending<DoclyDocument> { it.updatedAt }.thenBy { it.name.lowercase() }
        SortMode.UPDATED_ASC -> compareBy<DoclyDocument> { it.updatedAt }.thenBy { it.name.lowercase() }
        SortMode.NAME_ASC -> compareBy { it.name.lowercase() }
        SortMode.NAME_DESC -> compareByDescending { it.name.lowercase() }
        SortMode.TYPE_ASC -> compareBy<DoclyDocument> { it.type.name }.thenBy { it.name.lowercase() }
        SortMode.SIZE_DESC -> compareByDescending<DoclyDocument> { it.fileSize }.thenBy { it.name.lowercase() }
    }

    private fun List<DoclyDocument>.findById(documentId: String): DoclyDocument? =
        firstOrNull { document -> document.id == documentId }

    private fun DoclyDocument.internalPathOrNull(): String? = (fileRef as? FileRef.InternalFile)?.path
}
