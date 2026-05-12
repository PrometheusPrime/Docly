package com.docly.app.domain.usecase.reader

import com.docly.app.core.reader.DocxReaderEngine
import com.docly.app.core.reader.ExtractedDocument
import com.docly.app.core.reader.HtmlReaderEngine
import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.PdfDocumentInfo
import com.docly.app.core.reader.PdfReaderEngine
import com.docly.app.core.reader.RenderedHtmlDocument
import com.docly.app.core.reader.RenderedPdfPage
import com.docly.app.core.reader.TextReadChunk
import com.docly.app.core.reader.TextReaderEngine
import com.docly.app.core.reader.WorkbookDocument
import com.docly.app.core.reader.XlsxReaderEngine
import com.docly.app.core.reader.XlsxRowPage
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import javax.inject.Inject

class OpenPdfDocumentUseCase @Inject constructor(private val pdfReaderEngine: PdfReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef): AppResult<PdfDocumentInfo> = pdfReaderEngine.open(fileRef)
}

class RenderPdfPageUseCase @Inject constructor(private val pdfReaderEngine: PdfReaderEngine) {
    suspend operator fun invoke(
        documentId: String,
        fileRef: FileRef,
        pageIndex: Int,
        widthPx: Int,
        zoom: Float
    ): AppResult<RenderedPdfPage> = pdfReaderEngine.renderPage(
        documentId = documentId,
        fileRef = fileRef,
        pageIndex = pageIndex,
        widthPx = widthPx,
        zoom = zoom
    )
}

class ReadTextChunkUseCase @Inject constructor(private val textReaderEngine: TextReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef, offset: Long = 0L): AppResult<TextReadChunk> =
        textReaderEngine.readChunk(fileRef = fileRef, offset = offset)
}

class RenderMarkdownUseCase @Inject constructor(private val markdownReaderEngine: MarkdownReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef): AppResult<RenderedHtmlDocument> =
        markdownReaderEngine.render(fileRef)
}

class ReadHtmlUseCase @Inject constructor(private val htmlReaderEngine: HtmlReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef): AppResult<RenderedHtmlDocument> = htmlReaderEngine.read(fileRef)
}

class ParseDocxUseCase @Inject constructor(private val docxReaderEngine: DocxReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef): AppResult<ExtractedDocument> = docxReaderEngine.parse(fileRef)
}

class OpenXlsxUseCase @Inject constructor(private val xlsxReaderEngine: XlsxReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef): AppResult<WorkbookDocument> = xlsxReaderEngine.open(fileRef)
}

class ReadXlsxRowsUseCase @Inject constructor(private val xlsxReaderEngine: XlsxReaderEngine) {
    suspend operator fun invoke(fileRef: FileRef, sheetIndex: Int, startRowIndex: Int): AppResult<XlsxRowPage> =
        xlsxReaderEngine.readRows(
            fileRef = fileRef,
            sheetIndex = sheetIndex,
            startRowIndex = startRowIndex
        )
}
