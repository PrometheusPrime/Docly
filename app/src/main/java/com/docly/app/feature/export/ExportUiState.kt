package com.docly.app.feature.export

data class ExportUiState(
    val sessionId: String = "",
    val fileName: String = "",
    val pageCount: Int = 0,
    val isExporting: Boolean = false,
    val exportSuccessPath: String? = null,
    val errorMessage: String? = null
)
