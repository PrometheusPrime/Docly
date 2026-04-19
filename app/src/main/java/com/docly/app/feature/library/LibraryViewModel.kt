package com.docly.app.feature.library

import androidx.lifecycle.ViewModel
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveSavedDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val observeSavedDocumentsUseCase: ObserveSavedDocumentsUseCase,
    private val deleteSavedDocumentUseCase: DeleteSavedDocumentUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
}
