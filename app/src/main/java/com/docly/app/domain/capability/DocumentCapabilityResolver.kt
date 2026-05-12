package com.docly.app.domain.capability

import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentCapabilities
import com.docly.app.domain.model.DocumentType
import javax.inject.Inject

class DocumentCapabilityResolver @Inject constructor() {
    fun resolve(document: DoclyDocument): DocumentCapabilities {
        val baseCapabilities = resolve(document.type)
        return if (document.type == DocumentType.PDF && !document.sourceScanSessionId.isNullOrBlank()) {
            baseCapabilities.copy(canManagePages = true)
        } else {
            baseCapabilities
        }
    }

    fun resolve(type: DocumentType): DocumentCapabilities = when (type) {
        DocumentType.PDF -> DocumentCapabilities(
            canView = true,
            canCreate = true,
            canEdit = false,
            canAnnotate = false,
            canConvert = false
        )

        DocumentType.TXT -> DocumentCapabilities(
            canView = true,
            canCreate = true,
            canEdit = true,
            canAnnotate = false,
            canConvert = true,
            supportedOutputs = setOf(DocumentType.PDF, DocumentType.HTML, DocumentType.MARKDOWN)
        )

        DocumentType.MARKDOWN -> DocumentCapabilities(
            canView = true,
            canCreate = true,
            canEdit = true,
            canAnnotate = false,
            canConvert = true,
            supportedOutputs = setOf(DocumentType.PDF, DocumentType.HTML, DocumentType.TXT)
        )

        DocumentType.HTML -> DocumentCapabilities(
            canView = true,
            canCreate = true,
            canEdit = true,
            canAnnotate = false,
            canConvert = true,
            supportedOutputs = setOf(DocumentType.PDF, DocumentType.TXT)
        )

        DocumentType.IMAGE -> DocumentCapabilities(
            canView = true,
            canCreate = true,
            canEdit = false,
            canAnnotate = false,
            canConvert = true,
            supportedOutputs = setOf(DocumentType.PDF)
        )

        DocumentType.DOCX -> DocumentCapabilities(
            canView = true,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            isSimplifiedView = true,
            limitationMessage = DOCX_SIMPLIFIED_MESSAGE
        )

        DocumentType.XLSX -> DocumentCapabilities(
            canView = true,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            isSimplifiedView = true,
            limitationMessage = XLSX_SIMPLIFIED_MESSAGE
        )

        DocumentType.CSV -> DocumentCapabilities(
            canView = false,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            limitationMessage = "CSV support is planned with table tools."
        )

        DocumentType.UNKNOWN -> DocumentCapabilities(
            canView = false,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            limitationMessage = "Docly cannot open this file type yet."
        )
    }

    private companion object {
        const val DOCX_SIMPLIFIED_MESSAGE =
            "This DOCX is shown in a simplified view. Advanced layout, images, and comments are not shown."
        const val XLSX_SIMPLIFIED_MESSAGE =
            "This XLSX is shown as simplified tables. Formulas, charts, and formatting are not shown."
    }
}
