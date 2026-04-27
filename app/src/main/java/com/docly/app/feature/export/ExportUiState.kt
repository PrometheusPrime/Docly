package com.docly.app.feature.export

data class ExportUiState(
    val sessionId: String = "",
    val fileName: String = "",
    val title: String = "",
    val metadataSummary: String = "",
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val isExportReady: Boolean = false,
    val isExporting: Boolean = false,
    val exportedDocumentId: String? = null,
    val exportedPdfPath: String? = null,
    val errorMessage: String? = null
) {
    val canExport: Boolean
        get() = sessionId.isNotBlank() &&
            isExportReady &&
            !isLoading &&
            !isExporting &&
            exportedPdfPath == null

    val hasExportedPdf: Boolean
        get() = !exportedPdfPath.isNullOrBlank()
}
