package com.docly.app.domain.usecase.create

import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.pdf.HtmlToPdfExporter
import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.RenderedHtmlDocument
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.storage.DoclyStorageManager
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.repository.DocumentRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class DefaultDocumentContentFactory @Inject constructor() {
    fun create(type: DocumentType, title: String): String = when (type) {
        DocumentType.TXT -> ""

        DocumentType.MARKDOWN -> "# ${title.trim()}\n\n"

        DocumentType.HTML -> """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>${title.trim().escapeHtml()}</title>
            </head>
            <body>
            </body>
            </html>
        """.trimIndent()

        else -> ""
    }
}

class CreateDocumentUseCase @Inject constructor(
    private val storageManager: DoclyStorageManager,
    private val documentRepository: DocumentRepository,
    private val defaultDocumentContentFactory: DefaultDocumentContentFactory,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(title: String, type: DocumentType): AppResult<DoclyDocument> {
        if (type !in EDITABLE_DOCUMENT_TYPES) {
            return validationError("This document type cannot be created yet.")
        }

        val displayTitle = title.toDisplayTitle(type)
        if (displayTitle.isBlank()) {
            return validationError("Document title is required.")
        }

        val filePath = when (val pathResult = storageManager.createDocumentFile(displayTitle, type)) {
            is AppResult.Error -> return pathResult
            is AppResult.Success -> pathResult.data
        }

        val file = File(filePath)
        val content = defaultDocumentContentFactory.create(type = type, title = displayTitle)
        val writeResult = writeTextFile(file = file, content = content)
        if (writeResult is AppResult.Error) {
            storageManager.deleteFile(FileRef.InternalFile(filePath))
            return writeResult
        }

        val now = timeProvider.now()
        val document = DoclyDocument(
            id = idProvider.generateId(),
            name = displayTitle,
            type = type,
            mimeType = type.editableMimeType(),
            fileRef = FileRef.InternalFile(filePath),
            source = DocumentSource.CREATED,
            fileSize = file.length(),
            pageCount = null,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
            isFavorite = false,
            isScanned = false,
            ocrStatus = OcrStatus.NOT_STARTED
        )

        return when (val saveResult = documentRepository.upsertDocument(document)) {
            is AppResult.Error -> {
                storageManager.deleteFile(FileRef.InternalFile(filePath))
                saveResult
            }

            is AppResult.Success -> AppResult.Success(document)
        }
    }

    private suspend fun writeTextFile(file: File, content: String): AppResult<Unit> =
        withContext(dispatcherProvider.io) {
            try {
                file.parentFile?.mkdirs()
                file.writeText(content, Charsets.UTF_8)
                AppResult.Success(Unit)
            } catch (throwable: Throwable) {
                file.delete()
                AppResult.Error(
                    message = "Could not create document file.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        }
}

class LoadEditableDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(documentId: String): AppResult<EditableDocument> {
        val document = when (val documentResult = documentRepository.getDocument(documentId.trim())) {
            is AppResult.Error -> return documentResult
            is AppResult.Success -> documentResult.data ?: return validationError("Document not found.")
        }
        val file = when (val fileResult = document.editableInternalFile()) {
            is AppResult.Error -> return fileResult
            is AppResult.Success -> fileResult.data
        }

        return withContext(dispatcherProvider.io) {
            try {
                if (!file.isFile) {
                    return@withContext AppResult.Error(
                        message = "Document file not found.",
                        category = AppErrorCategory.STORAGE
                    )
                }
                if (file.length() > MAX_EDITABLE_TEXT_FILE_BYTES) {
                    return@withContext validationError("This document is too large to edit in this phase.")
                }
                AppResult.Success(
                    EditableDocument(
                        document = document,
                        content = file.readText(Charsets.UTF_8)
                    )
                )
            } catch (throwable: Throwable) {
                AppResult.Error(
                    message = "Could not load document.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        }
    }
}

class SaveEditableDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(
        documentId: String,
        content: String,
        expectedUpdatedAt: Long? = null
    ): AppResult<DoclyDocument> {
        val document = when (val documentResult = documentRepository.getDocument(documentId.trim())) {
            is AppResult.Error -> return documentResult
            is AppResult.Success -> documentResult.data ?: return validationError("Document not found.")
        }
        if (expectedUpdatedAt != null && document.updatedAt != expectedUpdatedAt) {
            return validationError("This document changed elsewhere. Reload before saving.")
        }
        val file = when (val fileResult = document.editableInternalFile()) {
            is AppResult.Error -> return fileResult
            is AppResult.Success -> fileResult.data
        }

        val updatedDocument = withContext(dispatcherProvider.io) {
            try {
                file.parentFile?.mkdirs()
                file.writeTextAtomically(content)
                AppResult.Success(
                    document.copy(
                        fileSize = file.length(),
                        updatedAt = timeProvider.now()
                    )
                )
            } catch (throwable: Throwable) {
                AppResult.Error(
                    message = "Could not save document.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        }
        if (updatedDocument is AppResult.Error) return updatedDocument

        val savedDocument = (updatedDocument as AppResult.Success).data
        return when (val saveResult = documentRepository.upsertDocument(savedDocument)) {
            is AppResult.Error -> saveResult
            is AppResult.Success -> AppResult.Success(savedDocument)
        }
    }
}

class RenderEditablePreviewUseCase @Inject constructor(private val dispatcherProvider: DispatcherProvider) {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder()
        .extensions(extensions)
        .build()
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    suspend operator fun invoke(type: DocumentType, content: String, title: String): AppResult<RenderedHtmlDocument> =
        withContext(dispatcherProvider.default) {
            try {
                val html = when (type) {
                    DocumentType.MARKDOWN -> htmlRenderer.render(parser.parse(content)).wrapPreviewHtml()
                    DocumentType.HTML -> content.asPdfHtmlDocument()
                    else -> return@withContext validationError("Preview is not available for this document type.")
                }
                AppResult.Success(RenderedHtmlDocument(html = html))
            } catch (throwable: Throwable) {
                AppResult.Error(
                    message = "Could not render preview.",
                    category = AppErrorCategory.PROCESSING,
                    throwable = throwable
                )
            }
        }
}

class CreatePdfFromTextDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val storageManager: DoclyStorageManager,
    private val markdownReaderEngine: MarkdownReaderEngine,
    private val htmlToPdfExporter: HtmlToPdfExporter,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
    private val dispatcherProvider: DispatcherProvider
) {
    suspend operator fun invoke(documentId: String): AppResult<DoclyDocument> {
        val sourceDocument = when (val documentResult = documentRepository.getDocument(documentId.trim())) {
            is AppResult.Error -> return documentResult
            is AppResult.Success -> documentResult.data ?: return validationError("Document not found.")
        }
        val sourceFile = when (val fileResult = sourceDocument.editableInternalFile()) {
            is AppResult.Error -> return fileResult
            is AppResult.Success -> fileResult.data
        }

        val html = when (val htmlResult = sourceDocument.renderHtmlForPdf(sourceFile)) {
            is AppResult.Error -> return htmlResult
            is AppResult.Success -> htmlResult.data
        }
        val outputPath = when (
            val pathResult = storageManager.createDocumentFile(sourceDocument.name, DocumentType.PDF)
        ) {
            is AppResult.Error -> return pathResult
            is AppResult.Success -> pathResult.data
        }

        when (val pdfResult = htmlToPdfExporter.generate(html = html, outputPdfPath = outputPath)) {
            is AppResult.Error -> {
                storageManager.deleteFile(FileRef.InternalFile(outputPath))
                return pdfResult
            }

            is AppResult.Success -> Unit
        }

        val outputFile = File(outputPath)
        val now = timeProvider.now()
        val pdfDocument = DoclyDocument(
            id = idProvider.generateId(),
            name = sourceDocument.name,
            type = DocumentType.PDF,
            mimeType = PDF_MIME_TYPE,
            fileRef = FileRef.InternalFile(outputPath),
            source = DocumentSource.CREATED,
            folderId = sourceDocument.folderId,
            thumbnailPath = null,
            fileSize = outputFile.length(),
            pageCount = null,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
            isFavorite = false,
            isScanned = false,
            ocrStatus = OcrStatus.NOT_STARTED
        )

        return when (val saveResult = documentRepository.upsertDocument(pdfDocument)) {
            is AppResult.Error -> {
                storageManager.deleteFile(FileRef.InternalFile(outputPath))
                saveResult
            }

            is AppResult.Success -> AppResult.Success(pdfDocument)
        }
    }

    private suspend fun DoclyDocument.renderHtmlForPdf(sourceFile: File): AppResult<String> = when (type) {
        DocumentType.TXT -> readSourceText(sourceFile).mapSuccess { text ->
            text.toPlainTextPdfHtml(title = name)
        }

        DocumentType.MARKDOWN -> markdownReaderEngine.render(fileRef).mapSuccess { rendered ->
            rendered.html
        }

        DocumentType.HTML -> readSourceText(sourceFile).mapSuccess { html ->
            html.asPdfHtmlDocument()
        }

        else -> validationError("This document type cannot be exported to PDF yet.")
    }

    private suspend fun readSourceText(sourceFile: File): AppResult<String> = withContext(dispatcherProvider.io) {
        try {
            if (!sourceFile.isFile) {
                return@withContext AppResult.Error(
                    message = "Document file not found.",
                    category = AppErrorCategory.STORAGE
                )
            }
            if (sourceFile.length() > MAX_EDITABLE_TEXT_FILE_BYTES) {
                return@withContext validationError("This document is too large to export in this phase.")
            }
            AppResult.Success(sourceFile.readText(Charsets.UTF_8))
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Could not read document.",
                category = AppErrorCategory.STORAGE,
                throwable = throwable
            )
        }
    }
}

data class EditableDocument(val document: DoclyDocument, val content: String)

private val EDITABLE_DOCUMENT_TYPES = setOf(DocumentType.TXT, DocumentType.MARKDOWN, DocumentType.HTML)

private const val MAX_EDITABLE_TEXT_FILE_BYTES = 2L * 1024L * 1024L
private const val PDF_MIME_TYPE = "application/pdf"

private fun DoclyDocument.editableInternalFile(): AppResult<File> {
    if (type !in EDITABLE_DOCUMENT_TYPES) {
        return validationError("This document type cannot be edited yet.")
    }
    val internalFile = fileRef as? FileRef.InternalFile
        ?: return validationError("Only local text documents can be edited in this phase.")
    return AppResult.Success(File(internalFile.path))
}

private fun DocumentType.editableMimeType(): String = when (this) {
    DocumentType.TXT -> "text/plain"
    DocumentType.MARKDOWN -> "text/markdown"
    DocumentType.HTML -> "text/html"
    else -> "application/octet-stream"
}

private fun String.toDisplayTitle(type: DocumentType): String {
    val trimmed = trim()
    val extensionPattern = when (type) {
        DocumentType.TXT -> Regex("\\.txt$", RegexOption.IGNORE_CASE)
        DocumentType.MARKDOWN -> Regex("\\.(md|markdown)$", RegexOption.IGNORE_CASE)
        DocumentType.HTML -> Regex("\\.(html|htm)$", RegexOption.IGNORE_CASE)
        else -> return trimmed
    }
    return trimmed.replace(extensionPattern, "").trim()
}

private fun String.toPlainTextPdfHtml(title: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${title.escapeHtml()}</title>
        <style>
            body { font-family: sans-serif; line-height: 1.5; color: #202124; }
            pre { white-space: pre-wrap; overflow-wrap: anywhere; font-family: sans-serif; }
        </style>
    </head>
    <body>
        <pre>${escapeHtml()}</pre>
    </body>
    </html>
""".trimIndent()

private fun String.asPdfHtmlDocument(): String = if (contains("<html", ignoreCase = true)) {
    this
} else {
    """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
        </head>
        <body>
            $this
        </body>
        </html>
    """.trimIndent()
}

private fun String.wrapPreviewHtml(): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
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

private fun validationError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.VALIDATION
)

private inline fun <T, R> AppResult<T>.mapSuccess(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Error -> this
    is AppResult.Success -> AppResult.Success(transform(data))
}

private fun File.writeTextAtomically(content: String) {
    val parent = parentFile ?: throw IllegalStateException("Document file has no parent directory.")
    val tempFile = File.createTempFile(nameWithoutExtension, ".tmp", parent)
    try {
        tempFile.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (atomicMoveFailure: Throwable) {
            Files.move(tempFile.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
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
