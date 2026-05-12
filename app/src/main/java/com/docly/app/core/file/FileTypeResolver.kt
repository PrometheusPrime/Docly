package com.docly.app.core.file

import com.docly.app.domain.model.DocumentType
import java.util.Locale
import javax.inject.Inject

class FileTypeResolver @Inject constructor() {
    fun resolve(fileName: String, mimeType: String?): DocumentType {
        val normalizedMimeType = mimeType?.lowercase(Locale.US)?.substringBefore(';')?.trim()
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)

        return when {
            normalizedMimeType == "application/pdf" || extension == "pdf" -> DocumentType.PDF
            normalizedMimeType == "text/plain" || extension == "txt" -> DocumentType.TXT
            normalizedMimeType in MARKDOWN_MIME_TYPES || extension in MARKDOWN_EXTENSIONS -> DocumentType.MARKDOWN
            normalizedMimeType == "text/html" || extension in HTML_EXTENSIONS -> DocumentType.HTML
            normalizedMimeType in DOCX_MIME_TYPES || extension == "docx" -> DocumentType.DOCX
            normalizedMimeType in XLSX_MIME_TYPES || extension == "xlsx" -> DocumentType.XLSX
            normalizedMimeType == "text/csv" || extension == "csv" -> DocumentType.CSV
            normalizedMimeType?.startsWith("image/") == true || extension in IMAGE_EXTENSIONS -> DocumentType.IMAGE
            else -> DocumentType.UNKNOWN
        }
    }

    private companion object {
        val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
        val HTML_EXTENSIONS = setOf("html", "htm")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val MARKDOWN_MIME_TYPES = setOf("text/markdown", "text/x-markdown")
        val DOCX_MIME_TYPES = setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        val XLSX_MIME_TYPES = setOf(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }
}
