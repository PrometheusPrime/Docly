package com.docly.app.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.docly.app.domain.usecase.page.AddProcessedPageUseCase
import com.docly.app.domain.usecase.page.ProcessCapturedPageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val processCapturedPageUseCase: ProcessCapturedPageUseCase,
    private val addProcessedPageUseCase: AddProcessedPageUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ReviewUiState(sessionId = savedStateHandle.get<String>(SESSION_ID_KEY).orEmpty())
    )
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private companion object {
        const val SESSION_ID_KEY = "sessionId"
    }
}
