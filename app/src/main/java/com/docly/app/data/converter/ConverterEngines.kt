package com.docly.app.data.converter

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.pdf.HtmlToPdfExporter
import com.docly.app.core.reader.DocxReaderEngine
import com.docly.app.core.reader.ExtractedBlockStyle
import com.docly.app.core.reader.ExtractedDocumentBlock
import com.docly.app.core.reader.XlsxReaderEngine
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.converter.ConverterEngine
import com.docly.app.domain.converter.ConverterOutput
import com.docly.app.domain.model.ConversionPair
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.PdfRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentRenderer
import org.jsoup.Jsoup

class TextDocumentConverterEngine @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val htmlToPdfExporter: HtmlToPdfExporter
) : ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.TXT, DocumentType.PDF),
        ConversionPair(DocumentType.TXT, DocumentType.HTML),
        ConversionPair(DocumentType.TXT, DocumentType.MARKDOWN)
    )

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val text = when (val result = sourceDocument.readInternalText(dispatcherProvider)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        return when (request.outputType) {
            DocumentType.PDF -> htmlToPdfExporter.generate(
                html = text.toPlainTextHtml(title = sourceDocument.name),
                outputPdfPath = outputPath
            ).toConverterOutput(PDF_MIME_TYPE)

            DocumentType.HTML -> writeTextOutput(
                outputPath = outputPath,
                content = text.toPlainTextHtml(title = sourceDocument.name),
                mimeType = HTML_MIME_TYPE
            )

            DocumentType.MARKDOWN -> writeTextOutput(
                outputPath = outputPath,
                content = text,
                mimeType = MARKDOWN_MIME_TYPE
            )

            else -> unsupported(request)
        }
    }
}

class MarkdownDocumentConverterEngine @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val htmlToPdfExporter: HtmlToPdfExporter
) : ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.MARKDOWN, DocumentType.HTML),
        ConversionPair(DocumentType.MARKDOWN, DocumentType.PDF),
        ConversionPair(DocumentType.MARKDOWN, DocumentType.TXT)
    )

    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder()
        .extensions(extensions)
        .build()
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()
    private val textRenderer = TextContentRenderer.builder()
        .extensions(extensions)
        .build()

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val markdown = when (val result = sourceDocument.readInternalText(dispatcherProvider)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }
        val node = parser.parse(markdown)

        return when (request.outputType) {
            DocumentType.HTML -> writeTextOutput(
                outputPath = outputPath,
                content = htmlRenderer.render(node).wrapHtmlDocument(title = sourceDocument.name),
                mimeType = HTML_MIME_TYPE
            )

            DocumentType.PDF -> htmlToPdfExporter.generate(
                html = htmlRenderer.render(node).wrapHtmlDocument(title = sourceDocument.name),
                outputPdfPath = outputPath
            ).toConverterOutput(PDF_MIME_TYPE)

            DocumentType.TXT -> writeTextOutput(
                outputPath = outputPath,
                content = textRenderer.render(node).trimEnd() + "\n",
                mimeType = TXT_MIME_TYPE
            )

            else -> unsupported(request)
        }
    }
}

class HtmlDocumentConverterEngine @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val htmlToPdfExporter: HtmlToPdfExporter
) : ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.HTML, DocumentType.PDF),
        ConversionPair(DocumentType.HTML, DocumentType.TXT)
    )

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val sourceHtml = when (val result = sourceDocument.readInternalText(dispatcherProvider)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        return when (request.outputType) {
            DocumentType.PDF -> htmlToPdfExporter.generate(
                html = sourceHtml.asHtmlDocument(title = sourceDocument.name),
                outputPdfPath = outputPath
            ).toConverterOutput(PDF_MIME_TYPE)

            DocumentType.TXT -> writeTextOutput(
                outputPath = outputPath,
                content = Jsoup.parse(sourceHtml).body().text() + "\n",
                mimeType = TXT_MIME_TYPE
            )

            else -> unsupported(request)
        }
    }
}

class ImageDocumentConverterEngine @Inject constructor(private val pdfRepository: PdfRepository) : ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.IMAGE, DocumentType.PDF)
    )

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val imagePath = when (val result = sourceDocument.requireInternalFile()) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data.absolutePath
        }

        return pdfRepository.createPdf(
            pageImagePaths = listOf(imagePath),
            outputPdfPath = outputPath
        ).toConverterOutput(mimeType = PDF_MIME_TYPE, pageCount = 1)
    }
}

class DocxDocumentConverterEngine @Inject constructor(private val docxReaderEngine: DocxReaderEngine) :
    ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.DOCX, DocumentType.TXT),
        ConversionPair(DocumentType.DOCX, DocumentType.HTML)
    )

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val extractedDocument = when (val result = docxReaderEngine.parse(sourceDocument.fileRef)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        return when (request.outputType) {
            DocumentType.TXT -> writeTextOutput(
                outputPath = outputPath,
                content = extractedDocument.blocks.toPlainText(),
                mimeType = TXT_MIME_TYPE
            )

            DocumentType.HTML -> writeTextOutput(
                outputPath = outputPath,
                content = extractedDocument.blocks.toSimpleHtml(title = sourceDocument.name),
                mimeType = HTML_MIME_TYPE
            )

            else -> unsupported(request)
        }
    }
}

class XlsxDocumentConverterEngine @Inject constructor(private val xlsxReaderEngine: XlsxReaderEngine) :
    ConverterEngine {
    override val supportedPairs: Set<ConversionPair> = setOf(
        ConversionPair(DocumentType.XLSX, DocumentType.CSV),
        ConversionPair(DocumentType.XLSX, DocumentType.TXT)
    )

    override suspend fun convert(
        request: ConversionRequest,
        sourceDocument: DoclyDocument,
        outputPath: String
    ): AppResult<ConverterOutput> {
        val rows = when (val result = readAllRows(request = request, sourceDocument = sourceDocument)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        return when (request.outputType) {
            DocumentType.CSV -> writeTextOutput(
                outputPath = outputPath,
                content = rows.toCsv(),
                mimeType = CSV_MIME_TYPE
            )

            DocumentType.TXT -> writeTextOutput(
                outputPath = outputPath,
                content = rows.toTabSeparatedText(),
                mimeType = TXT_MIME_TYPE
            )

            else -> unsupported(request)
        }
    }

    private suspend fun readAllRows(
        request: ConversionRequest,
        sourceDocument: DoclyDocument
    ): AppResult<List<List<String>>> {
        val rows = mutableListOf<List<String>>()
        var nextRowIndex = 0
        do {
            val page = when (
                val result = xlsxReaderEngine.readRows(
                    fileRef = sourceDocument.fileRef,
                    sheetIndex = request.options.xlsxSheetIndex,
                    startRowIndex = nextRowIndex,
                    maxRows = XLSX_CONVERSION_ROW_PAGE_SIZE
                )
            ) {
                is AppResult.Error -> return result
                is AppResult.Success -> result.data
            }
            rows += page.rows
            nextRowIndex = page.nextRowIndex ?: 0
        } while (page.hasMore)
        return AppResult.Success(rows)
    }

    private companion object {
        const val XLSX_CONVERSION_ROW_PAGE_SIZE = 200
    }
}

private suspend fun DoclyDocument.readInternalText(dispatcherProvider: DispatcherProvider): AppResult<String> =
    withContext(dispatcherProvider.io) {
        val file = when (val result = requireInternalFile()) {
            is AppResult.Error -> return@withContext result
            is AppResult.Success -> result.data
        }

        try {
            if (!file.isFile) {
                return@withContext storageError("Document file not found.")
            }
            if (file.length() > MAX_TEXT_CONVERSION_BYTES) {
                return@withContext AppResult.Error(
                    message = "This document is too large to convert in this phase.",
                    category = AppErrorCategory.VALIDATION
                )
            }
            AppResult.Success(file.readText(Charsets.UTF_8))
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Could not read document.",
                category = AppErrorCategory.STORAGE,
                throwable = throwable
            )
        }
    }

private fun DoclyDocument.requireInternalFile(): AppResult<File> {
    val fileRef = fileRef as? FileRef.InternalFile
        ?: return AppResult.Error(
            message = "Only local library documents can be converted.",
            category = AppErrorCategory.VALIDATION
        )
    return AppResult.Success(File(fileRef.path))
}

private suspend fun writeTextOutput(outputPath: String, content: String, mimeType: String): AppResult<ConverterOutput> =
    try {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(content, Charsets.UTF_8)
        AppResult.Success(ConverterOutput(outputPath = outputPath, mimeType = mimeType))
    } catch (throwable: Throwable) {
        AppResult.Error(
            message = "Could not write converted document.",
            category = AppErrorCategory.STORAGE,
            throwable = throwable
        )
    }

private fun AppResult<String>.toConverterOutput(mimeType: String, pageCount: Int? = null): AppResult<ConverterOutput> =
    when (this) {
        is AppResult.Error -> this

        is AppResult.Success -> AppResult.Success(
            ConverterOutput(outputPath = data, mimeType = mimeType, pageCount = pageCount)
        )
    }

private fun unsupported(request: ConversionRequest): AppResult.Error = AppResult.Error(
    message = "${request.inputType.label} to ${request.outputType.label} is not supported yet.",
    category = AppErrorCategory.VALIDATION
)

private fun storageError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.STORAGE
)

private fun String.toPlainTextHtml(title: String): String = "<pre>${escapeHtml()}</pre>".wrapHtmlDocument(title = title)

private fun String.asHtmlDocument(title: String): String = if (contains("<html", ignoreCase = true)) {
    this
} else {
    wrapHtmlDocument(title = title)
}

private fun String.wrapHtmlDocument(title: String): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${title.escapeHtml()}</title>
        <style>
          body {
            font-family: sans-serif;
            color: #202124;
            background: #ffffff;
            margin: 0;
            padding: 18px;
            line-height: 1.5;
            overflow-wrap: anywhere;
          }
          table {
            border-collapse: collapse;
            width: 100%;
            margin: 12px 0;
          }
          th, td {
            border: 1px solid #d0d7de;
            padding: 6px 8px;
            vertical-align: top;
          }
          pre, code {
            background: #f6f8fa;
            border-radius: 4px;
          }
          pre {
            overflow-x: auto;
            padding: 10px;
          }
        </style>
      </head>
      <body>
        $this
      </body>
    </html>
""".trimIndent()

private fun List<ExtractedDocumentBlock>.toPlainText(): String = joinToString(separator = "\n\n") { block ->
    when (block) {
        is ExtractedDocumentBlock.Paragraph -> when (block.style) {
            ExtractedBlockStyle.LIST_ITEM -> "- ${block.text}"
            else -> block.text
        }

        is ExtractedDocumentBlock.Table -> block.rows.toTabSeparatedText().trimEnd()
    }
}.trimEnd() + "\n"

private fun List<ExtractedDocumentBlock>.toSimpleHtml(title: String): String = joinToString(separator = "\n") { block ->
    when (block) {
        is ExtractedDocumentBlock.Paragraph -> when (block.style) {
            ExtractedBlockStyle.HEADING -> "<h2>${block.text.escapeHtml()}</h2>"
            ExtractedBlockStyle.LIST_ITEM -> "<ul><li>${block.text.escapeHtml()}</li></ul>"
            ExtractedBlockStyle.NORMAL -> "<p>${block.text.escapeHtml()}</p>"
        }

        is ExtractedDocumentBlock.Table -> block.rows.toHtmlTable()
    }
}.wrapHtmlDocument(title = title)

private fun List<List<String>>.toHtmlTable(): String = joinToString(
    prefix = "<table><tbody>",
    separator = "",
    postfix = "</tbody></table>"
) { row ->
    row.joinToString(prefix = "<tr>", separator = "", postfix = "</tr>") { cell ->
        "<td>${cell.escapeHtml()}</td>"
    }
}

private fun List<List<String>>.toCsv(): String = joinToString(separator = "\n", postfix = "\n") { row ->
    row.joinToString(separator = ",") { cell -> cell.toCsvCell() }
}

private fun String.toCsvCell(): String {
    val needsQuotes = any { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }
    val escaped = replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

private fun List<List<String>>.toTabSeparatedText(): String = joinToString(separator = "\n", postfix = "\n") { row ->
    row.joinToString(separator = "\t") { cell ->
        cell.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    }
}

private fun String.escapeHtml(): String = buildString(length) {
    this@escapeHtml.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

private val DocumentType.label: String
    get() = when (this) {
        DocumentType.PDF -> "PDF"
        DocumentType.TXT -> "TXT"
        DocumentType.MARKDOWN -> "Markdown"
        DocumentType.HTML -> "HTML"
        DocumentType.DOCX -> "DOCX"
        DocumentType.XLSX -> "XLSX"
        DocumentType.CSV -> "CSV"
        DocumentType.IMAGE -> "Image"
        DocumentType.UNKNOWN -> "Unknown"
    }

private const val MAX_TEXT_CONVERSION_BYTES = 2L * 1024L * 1024L
private const val PDF_MIME_TYPE = "application/pdf"
private const val TXT_MIME_TYPE = "text/plain"
private const val MARKDOWN_MIME_TYPE = "text/markdown"
private const val HTML_MIME_TYPE = "text/html"
private const val CSV_MIME_TYPE = "text/csv"
