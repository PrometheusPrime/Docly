package com.docly.app.core.reader

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef

data class PdfDocumentInfo(val pageCount: Int)

data class RenderedPdfPage(val pageIndex: Int, val width: Int, val height: Int, val imagePath: String)

data class TextReadChunk(val lines: List<String>, val nextOffset: Long?, val hasMore: Boolean)

data class RenderedHtmlDocument(val html: String)

enum class ExtractedBlockStyle {
    NORMAL,
    HEADING,
    LIST_ITEM
}

sealed interface ExtractedDocumentBlock {
    data class Paragraph(val text: String, val style: ExtractedBlockStyle) : ExtractedDocumentBlock
    data class Table(val rows: List<List<String>>) : ExtractedDocumentBlock
}

data class ExtractedDocument(val blocks: List<ExtractedDocumentBlock>)

data class XlsxSheetInfo(val name: String, val index: Int)

data class WorkbookDocument(val sheets: List<XlsxSheetInfo>)

data class XlsxRowPage(val rows: List<List<String>>, val nextRowIndex: Int?, val hasMore: Boolean)

interface PdfReaderEngine {
    suspend fun open(fileRef: FileRef): AppResult<PdfDocumentInfo>

    suspend fun renderPage(
        documentId: String,
        fileRef: FileRef,
        pageIndex: Int,
        widthPx: Int,
        zoom: Float
    ): AppResult<RenderedPdfPage>
}

interface TextReaderEngine {
    suspend fun readChunk(
        fileRef: FileRef,
        offset: Long = 0L,
        maxChars: Int = DEFAULT_TEXT_CHUNK_CHARS
    ): AppResult<TextReadChunk>

    companion object {
        const val DEFAULT_TEXT_CHUNK_CHARS = 32_000
    }
}

interface MarkdownReaderEngine {
    suspend fun render(fileRef: FileRef): AppResult<RenderedHtmlDocument>
}

interface HtmlReaderEngine {
    suspend fun read(fileRef: FileRef): AppResult<RenderedHtmlDocument>
}

interface DocxReaderEngine {
    suspend fun parse(fileRef: FileRef): AppResult<ExtractedDocument>
}

interface XlsxReaderEngine {
    suspend fun open(fileRef: FileRef): AppResult<WorkbookDocument>

    suspend fun readRows(
        fileRef: FileRef,
        sheetIndex: Int,
        startRowIndex: Int,
        maxRows: Int = DEFAULT_XLSX_ROW_PAGE_SIZE
    ): AppResult<XlsxRowPage>

    companion object {
        const val DEFAULT_XLSX_ROW_PAGE_SIZE = 40
    }
}
