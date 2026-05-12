package com.docly.app.domain.reader

import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import javax.inject.Inject

sealed interface DocumentOpenTarget {
    data class Reader(val documentId: String) : DocumentOpenTarget
    data class ExternalViewer(val filePath: String, val mimeType: String?) : DocumentOpenTarget
    data class Unsupported(val message: String) : DocumentOpenTarget
}

class DocumentOpenResolver @Inject constructor(private val capabilityResolver: DocumentCapabilityResolver) {
    fun resolve(document: DoclyDocument): DocumentOpenTarget {
        val capabilities = capabilityResolver.resolve(document)
        if (!capabilities.canView) {
            return DocumentOpenTarget.Unsupported(
                capabilities.limitationMessage ?: "Docly cannot open this file type yet."
            )
        }

        return when (document.type) {
            DocumentType.PDF,
            DocumentType.TXT,
            DocumentType.MARKDOWN,
            DocumentType.HTML,
            DocumentType.DOCX,
            DocumentType.XLSX -> DocumentOpenTarget.Reader(document.id)

            DocumentType.IMAGE -> {
                val path = (document.fileRef as? FileRef.InternalFile)?.path
                if (path.isNullOrBlank()) {
                    DocumentOpenTarget.Unsupported("Document file not found.")
                } else {
                    DocumentOpenTarget.ExternalViewer(filePath = path, mimeType = document.mimeType)
                }
            }

            DocumentType.CSV,
            DocumentType.UNKNOWN -> DocumentOpenTarget.Unsupported(
                capabilities.limitationMessage ?: "Docly cannot open this file type yet."
            )
        }
    }
}
