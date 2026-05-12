package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.DocumentEntity
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.OcrStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentMapperTest {
    @Test
    fun documentEntityMapsToDomainAndBack() {
        val entity = DocumentEntity(
            id = "document-id",
            name = "Paper",
            type = DocumentType.PDF.name,
            mimeType = "application/pdf",
            filePath = "/files/paper.pdf",
            uri = null,
            source = DocumentSource.IMPORTED.name,
            folderId = "folder-id",
            thumbnailPath = "/thumb.jpg",
            fileSize = 128L,
            pageCount = 2,
            createdAt = 1L,
            updatedAt = 2L,
            lastOpenedAt = 3L,
            isFavorite = true,
            isScanned = false,
            ocrStatus = OcrStatus.NOT_STARTED.name,
            sourceScanSessionId = "scan-session"
        )

        val domain = entity.toDomain()

        assertEquals("Paper", domain.name)
        assertEquals(DocumentType.PDF, domain.type)
        assertEquals(FileRef.InternalFile("/files/paper.pdf"), domain.fileRef)
        assertEquals("scan-session", domain.sourceScanSessionId)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun externalUriRoundTrips() {
        val document = DoclyDocument(
            id = "document-id",
            name = "External",
            type = DocumentType.TXT,
            mimeType = "text/plain",
            fileRef = FileRef.ExternalUri("content://external"),
            source = DocumentSource.EXTERNAL_URI,
            fileSize = 10L,
            createdAt = 1L,
            updatedAt = 1L
        )

        assertEquals(document, document.toEntity().toDomain())
    }
}
