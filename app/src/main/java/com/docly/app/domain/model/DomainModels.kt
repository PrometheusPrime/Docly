package com.docly.app.domain.model

data class ScanSession(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ScanSessionStatus,
    val scanMode: ScanMode,
    val pages: List<ScannedPage> = emptyList(),
    val metadata: DocumentMetadata? = null
)

data class ScannedPage(
    val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val scanMode: ScanMode,
    val width: Int,
    val height: Int,
    val corners: PageCorners? = null,
    val createdAt: Long,
    val reviewStatus: PageReviewStatus = PageReviewStatus.ACCEPTED
)

data class DocumentMetadata(
    val grade: String,
    val subject: String,
    val year: Int,
    val paperType: String,
    val paperNumber: String? = null,
    val source: String? = null,
    val notes: String? = null
)

data class SavedDocument(
    val id: String,
    val sessionId: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val metadata: DocumentMetadata,
    val pageCount: Int,
    val createdAt: Long
)

data class PageCorners(
    val topLeft: PointFSerializable,
    val topRight: PointFSerializable,
    val bottomRight: PointFSerializable,
    val bottomLeft: PointFSerializable
)

data class PointFSerializable(val x: Float, val y: Float)

enum class ScanMode {
    DOCUMENT,
    MIXED,
    COLOR
}

enum class PageReviewStatus {
    PENDING,
    ACCEPTED
}

enum class ScanSessionStatus {
    IN_PROGRESS,
    READY_FOR_EXPORT,
    EXPORTED,
    ABANDONED
}

data class ProcessedPageResult(
    val processedImagePath: String,
    val thumbnailPath: String,
    val detectedCorners: PageCorners?,
    val width: Int,
    val height: Int
)

data class ImportedRawImage(val path: String, val width: Int, val height: Int)

data class CapturePageResult(val sessionId: String, val page: ScannedPage)

data class ImportDevicePhotosResult(val sessionId: String, val importedPages: List<ScannedPage>)

data class ValidationResult(val isValid: Boolean, val errors: List<String> = emptyList())
