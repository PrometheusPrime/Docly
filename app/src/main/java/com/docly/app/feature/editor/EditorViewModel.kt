package com.docly.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val reorderPagesUseCase: ReorderPagesUseCase,
    private val rotatePageUseCase: RotatePageUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EditorUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
