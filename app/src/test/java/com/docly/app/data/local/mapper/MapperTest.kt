package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.SavedDocumentEntity
import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapperTest {
    @Test
    fun scanSessionMapperRoundTripsCompleteMetadataAndPreservesSuppliedPages() {
        val entity = scanSessionEntity(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1",
            source = "School",
            notes = "Term 1"
        )
        val pages =
            listOf(scannedPage(id = "first-page", pageIndex = 0), scannedPage(id = "second-page", pageIndex = 1))

        val domain = entity.toDomain(pages = pages)

        assertEquals(pages, domain.pages)
        assertEquals(ScanSessionStatus.READY_FOR_EXPORT, domain.status)
        assertEquals(ScanMode.MIXED, domain.scanMode)
        assertEquals(
            DocumentMetadata(
                grade = "Grade 10",
                subject = "Math",
                year = 2026,
                paperType = "Past Paper",
                paperNumber = "1",
                source = "School",
                notes = "Term 1"
            ),
            domain.metadata
        )
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun scanSessionMapperKeepsMetadataNullUntilRequiredFieldsExistAndFallbacksEnums() {
        val entity = scanSessionEntity(
            status = "UNKNOWN_STATUS",
            scanMode = "UNKNOWN_MODE",
            grade = "Grade 10",
            subject = null,
            year = 2026,
            paperType = "Past Paper",
            paperNumber = null,
            source = null,
            notes = null
        )

        val domain = entity.toDomain()

        assertEquals(ScanSessionStatus.ABANDONED, domain.status)
        assertEquals(ScanMode.DOCUMENT, domain.scanMode)
        assertNull(domain.metadata)
    }

    @Test
    fun scannedPageMapperRoundTripsCorners() {
        val page = scannedPage(
            reviewStatus = PageReviewStatus.PENDING,
            corners = PageCorners(
                topLeft = PointFSerializable(1f, 2f),
                topRight = PointFSerializable(3f, 4f),
                bottomRight = PointFSerializable(5f, 6f),
                bottomLeft = PointFSerializable(7f, 8f)
            )
        )

        val mapped = page.toEntity().toDomain()

        assertEquals(page, mapped)
    }

    @Test
    fun scannedPageMapperDropsPartialCorners() {
        val entity = scannedPage().toEntity().copy(
            topLeftX = 1f,
            topLeftY = 2f,
            topRightX = null,
            topRightY = 4f,
            bottomRightX = 5f,
            bottomRightY = 6f,
            bottomLeftX = 7f,
            bottomLeftY = 8f
        )

        val domain = entity.toDomain()

        assertNull(domain.corners)
    }

    @Test
    fun savedDocumentMapperRoundTripsPopulatedMetadataFields() {
        val entity = savedDocumentEntity(
            paperNumber = "1",
            source = "School",
            notes = "Notes"
        )

        val domain = entity.toDomain()

        assertEquals("Grade 10", domain.metadata.grade)
        assertEquals("Math", domain.metadata.subject)
        assertEquals(2026, domain.metadata.year)
        assertEquals("Past Paper", domain.metadata.paperType)
        assertEquals("1", domain.metadata.paperNumber)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun savedDocumentMapperRoundTripsNullableMetadataFields() {
        val entity = savedDocumentEntity(
            thumbnailPath = null,
            paperNumber = null,
            source = null,
            notes = null
        )

        val domain = entity.toDomain()

        assertNull(domain.thumbnailPath)
        assertNull(domain.metadata.paperNumber)
        assertNull(domain.metadata.source)
        assertNull(domain.metadata.notes)
        assertEquals(entity, domain.toEntity())
    }

    private fun scanSessionEntity(
        id: String = "session-id",
        createdAt: Long = 1L,
        updatedAt: Long = 2L,
        status: String = ScanSessionStatus.READY_FOR_EXPORT.name,
        scanMode: String = ScanMode.MIXED.name,
        grade: String? = null,
        subject: String? = null,
        year: Int? = null,
        paperType: String? = null,
        paperNumber: String? = null,
        source: String? = null,
        notes: String? = null
    ): ScanSessionEntity = ScanSessionEntity(
        id = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        status = status,
        scanMode = scanMode,
        grade = grade,
        subject = subject,
        year = year,
        paperType = paperType,
        paperNumber = paperNumber,
        source = source,
        notes = notes
    )

    private fun scannedPage(
        id: String = "page-id",
        pageIndex: Int = 0,
        reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED,
        corners: PageCorners? = null
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/$id/raw.jpg",
        processedImagePath = "/$id/processed.jpg",
        thumbnailPath = "/$id/thumb.jpg",
        rotationDegrees = 90,
        scanMode = ScanMode.MIXED,
        width = 100,
        height = 200,
        corners = corners,
        createdAt = 3L,
        reviewStatus = reviewStatus
    )

    private fun savedDocumentEntity(
        thumbnailPath: String? = "/thumb.jpg",
        paperNumber: String? = null,
        source: String? = null,
        notes: String? = null
    ): SavedDocumentEntity = savedDocument(
        thumbnailPath = thumbnailPath,
        paperNumber = paperNumber,
        source = source,
        notes = notes
    ).toEntity()

    private fun savedDocument(
        thumbnailPath: String? = "/thumb.jpg",
        paperNumber: String? = null,
        source: String? = null,
        notes: String? = null
    ): SavedDocument = SavedDocument(
        id = "document-id",
        sessionId = "session-id",
        title = "Title",
        pdfPath = "/document.pdf",
        thumbnailPath = thumbnailPath,
        metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = paperNumber,
            source = source,
            notes = notes
        ),
        pageCount = 2,
        createdAt = 4L
    )
}
