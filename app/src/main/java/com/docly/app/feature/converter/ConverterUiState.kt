package com.docly.app.feature.converter

import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType

data class ConverterUiState(
    val documents: List<DoclyDocument> = emptyList(),
    val selectedDocumentId: String? = null,
    val selectedOutputType: DocumentType? = null,
    val supportedOutputTypes: List<DocumentType> = emptyList(),
    val outputFileName: String = "",
    val xlsxSheets: List<XlsxSheetInfo> = emptyList(),
    val selectedXlsxSheetIndex: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingSheets: Boolean = false,
    val isConverting: Boolean = false,
    val progress: Int = 0,
    val completedDocumentId: String? = null,
    val completedOutputPath: String? = null,
    val completedMimeType: String? = null,
    val errorMessage: String? = null
) {
    val hasConvertibleDocuments: Boolean
        get() = documents.isNotEmpty()

    val hasCompletedOutput: Boolean
        get() = !completedDocumentId.isNullOrBlank() && !completedOutputPath.isNullOrBlank()

    val canConvert: Boolean
        get() = !isLoading &&
            !isLoadingSheets &&
            !isConverting &&
            !selectedDocumentId.isNullOrBlank() &&
            selectedOutputType != null &&
            outputFileName.isNotBlank()
}
