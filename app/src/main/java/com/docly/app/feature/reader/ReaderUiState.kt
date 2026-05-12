package com.docly.app.feature.reader

import com.docly.app.core.reader.ExtractedDocumentBlock
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.domain.model.DocumentType

data class ReaderUiState(
    val documentId: String = "",
    val title: String = "Reader",
    val documentType: DocumentType? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val content: ReaderContent? = null,
    val textSizeSp: Float = 16f,
    val useDarkReaderTheme: Boolean = false
) {
    val canNavigatePdfPrevious: Boolean
        get() = (content as? ReaderContent.Pdf)?.let { it.currentPageIndex > 0 } == true

    val canNavigatePdfNext: Boolean
        get() = (content as? ReaderContent.Pdf)?.let { it.currentPageIndex < it.pageCount - 1 } == true
}

sealed interface ReaderContent {
    data class Pdf(
        val pageCount: Int,
        val currentPageIndex: Int,
        val renderedPagePath: String?,
        val renderedWidth: Int,
        val renderedHeight: Int,
        val zoom: Float,
        val targetWidthPx: Int
    ) : ReaderContent

    data class Text(val lines: List<String>, val nextOffset: Long?, val hasMore: Boolean) : ReaderContent

    data class Web(val html: String, val simplifiedMessage: String? = null) : ReaderContent

    data class Docx(val blocks: List<ExtractedDocumentBlock>, val simplifiedMessage: String) : ReaderContent

    data class Xlsx(
        val sheets: List<XlsxSheetInfo>,
        val selectedSheetIndex: Int,
        val rows: List<List<String>>,
        val nextRowIndex: Int?,
        val hasMore: Boolean,
        val simplifiedMessage: String
    ) : ReaderContent
}
