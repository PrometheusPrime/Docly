package com.docly.app.domain.reader

import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentOpenResolverTest {
    private val resolver = DocumentOpenResolver(DocumentCapabilityResolver())

    @Test
    fun readerTypesResolveToInAppReader() {
        listOf(
            DocumentType.PDF,
            DocumentType.TXT,
            DocumentType.MARKDOWN,
            DocumentType.HTML,
            DocumentType.DOCX,
            DocumentType.XLSX
        ).forEach { type ->
            assertEquals(DocumentOpenTarget.Reader("document-id"), resolver.resolve(document(type = type)))
        }
    }

    @Test
    fun imagesResolveToExternalViewer() {
        assertEquals(
            DocumentOpenTarget.ExternalViewer(filePath = "/docs/image.jpg", mimeType = "image/jpeg"),
            resolver.resolve(document(type = DocumentType.IMAGE, filePath = "/docs/image.jpg", mimeType = "image/jpeg"))
        )
    }

    @Test
    fun unsupportedTypesResolveToMessage() {
        assertTrue(resolver.resolve(document(type = DocumentType.CSV)) is DocumentOpenTarget.Unsupported)
        assertTrue(resolver.resolve(document(type = DocumentType.UNKNOWN)) is DocumentOpenTarget.Unsupported)
    }

    private fun document(
        type: DocumentType,
        filePath: String = "/docs/document",
        mimeType: String? = null
    ): DoclyDocument = DoclyDocument(
        id = "document-id",
        name = "Document",
        type = type,
        mimeType = mimeType,
        fileRef = FileRef.InternalFile(filePath),
        source = DocumentSource.IMPORTED,
        fileSize = 10L,
        createdAt = 1L,
        updatedAt = 1L
    )
}
