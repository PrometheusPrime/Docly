package com.docly.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.domain.usecase.page.DeletePageUseCase
import com.docly.app.domain.usecase.page.ReorderPagesUseCase
import com.docly.app.domain.usecase.page.RotatePageUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        loadSession()
    }

    private fun loadSession() {
        val sessionId = _uiState.value.sessionId
        if (sessionId.isBlank()) {
            _uiState.update { state ->
                state.copy(errorMessage = "Scan session not found.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }

            when (val result = getScanSessionUseCase(sessionId)) {
                is AppResult.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = result.toUserMessage()
                    )
                }

                is AppResult.Success -> _uiState.update { state ->
                    val session = result.data
                    state.copy(
                        pages = session?.pages.orEmpty().sortedBy { page -> page.pageIndex },
                        isLoading = false,
                        errorMessage = if (session == null) "Scan session not found." else null
                    )
                }
            }
        }
    }

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
