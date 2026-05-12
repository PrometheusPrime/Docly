package com.docly.app.domain.capability

import com.docly.app.domain.model.DocumentCapabilities
import com.docly.app.domain.model.DocumentType
import javax.inject.Inject

class DocumentCapabilityResolver @Inject constructor() {
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
            canView = false,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            isSimplifiedView = true,
            limitationMessage = "DOCX reading is planned after the core readers."
        )

        DocumentType.XLSX -> DocumentCapabilities(
            canView = false,
            canCreate = false,
            canEdit = false,
            canAnnotate = false,
            canConvert = false,
            isSimplifiedView = true,
            limitationMessage = "XLSX reading is planned after the core readers."
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
}
