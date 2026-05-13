package com.docly.app.domain.model

data class DoclyDocument(
    val id: String,
    val name: String,
    val type: DocumentType,
    val mimeType: String?,
    val fileRef: FileRef,
    val source: DocumentSource,
    val folderId: String? = null,
    val thumbnailPath: String? = null,
    val fileSize: Long = 0L,
    val pageCount: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
    val isFavorite: Boolean = false,
    val isScanned: Boolean = false,
    val ocrStatus: OcrStatus = OcrStatus.NOT_STARTED,
    val sourceScanSessionId: String? = null
)

enum class DocumentType {
    PDF,
    TXT,
    MARKDOWN,
    HTML,
    DOCX,
    XLSX,
    CSV,
    IMAGE,
    UNKNOWN
}

sealed class FileRef {
    data class InternalFile(val path: String) : FileRef()
    data class ExternalUri(val uri: String) : FileRef()
}

enum class DocumentSource {
    INTERNAL,
    EXTERNAL_URI,
    SCANNED,
    IMPORTED,
    CREATED,
    CONVERTED
}

data class DocumentCapabilities(
    val canView: Boolean,
    val canCreate: Boolean,
    val canEdit: Boolean,
    val canAnnotate: Boolean,
    val canConvert: Boolean,
    val supportedOutputs: Set<DocumentType> = emptySet(),
    val canManagePages: Boolean = false,
    val isSimplifiedView: Boolean = false,
    val limitationMessage: String? = null
)

enum class SortMode {
    UPDATED_DESC,
    UPDATED_ASC,
    NAME_ASC,
    NAME_DESC,
    TYPE_ASC,
    SIZE_DESC
}

enum class ViewMode {
    LIST,
    GRID
}

data class Folder(val id: String, val name: String, val parentId: String?, val createdAt: Long, val updatedAt: Long)

data class RecentDocument(val documentId: String, val openedAt: Long)

data class ConversionPair(val input: DocumentType, val output: DocumentType)

data class ConversionRequest(
    val inputDocumentId: String?,
    val inputUri: String? = null,
    val inputType: DocumentType,
    val outputType: DocumentType,
    val outputFileName: String,
    val options: ConversionOptions = ConversionOptions()
)

data class ConversionOptions(val xlsxSheetIndex: Int = 0)

data class ConversionResult(val job: ConversionJob, val outputDocument: DoclyDocument, val outputPath: String)

enum class ConversionStatus {
    IDLE,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ConversionJob(
    val id: String,
    val inputDocumentId: String?,
    val inputUri: String?,
    val inputType: DocumentType,
    val outputType: DocumentType,
    val outputPath: String?,
    val outputDocumentId: String?,
    val status: ConversionStatus,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class ScanSession(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: ScanSessionStatus,
    val scanMode: ScanMode,
    val pages: List<ScannedPage> = emptyList(),
    val metadata: DocumentMetadata? = null
)

data class RecoverableScanSession(val session: ScanSession, val destination: ScanSessionRecoveryDestination)

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

/**
 * Compatibility DTO for the scanner/export code that predates the unified document model.
 * New library code should use [DoclyDocument].
 */
data class SavedDocument(
    val id: String,
    val sessionId: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val metadata: DocumentMetadata,
    val pageCount: Int,
    val createdAt: Long,
    val ocrStatus: OcrStatus = OcrStatus.NOT_STARTED,
    val ocrText: String = "",
    val ocrUpdatedAt: Long? = null,
    val ocrLastError: String? = null
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

enum class ScanSessionRecoveryDestination {
    REVIEW,
    EDITOR,
    EXPORT
}

enum class OcrStatus {
    NOT_STARTED,
    QUEUED,
    RUNNING,
    COMPLETE,
    FAILED
}

enum class DiagnosticStage {
    OCR,
    PROCESSING,
    CAMERA_COMPATIBILITY,
    AUTO_CAPTURE
}

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

data class OrphanCleanupResult(val deletedFileCount: Int)

data class OcrResult(
    val documentId: String,
    val status: OcrStatus,
    val text: String = "",
    val updatedAt: Long,
    val lastError: String? = null
)

data class DiagnosticEvent(
    val id: String,
    val timestampMillis: Long,
    val stage: DiagnosticStage,
    val severity: DiagnosticSeverity,
    val message: String,
    val relatedDocumentId: String? = null,
    val relatedSessionId: String? = null,
    val relatedPageId: String? = null,
    val throwableClass: String? = null
)

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

fun SavedDocument.toDoclyDocument(updatedAt: Long = createdAt): DoclyDocument = DoclyDocument(
    id = id,
    name = title,
    type = DocumentType.PDF,
    mimeType = "application/pdf",
    fileRef = FileRef.InternalFile(pdfPath),
    source = DocumentSource.SCANNED,
    folderId = null,
    thumbnailPath = thumbnailPath,
    fileSize = 0L,
    pageCount = pageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = null,
    isFavorite = false,
    isScanned = true,
    ocrStatus = ocrStatus,
    sourceScanSessionId = sessionId.takeIf { it.isNotBlank() }
)

fun DoclyDocument.toSavedDocumentCompat(): SavedDocument = SavedDocument(
    id = id,
    sessionId = sourceScanSessionId.orEmpty(),
    title = name,
    pdfPath = (fileRef as? FileRef.InternalFile)?.path.orEmpty(),
    thumbnailPath = thumbnailPath,
    metadata = DocumentMetadata(
        grade = "",
        subject = "",
        year = 2026,
        paperType = ""
    ),
    pageCount = pageCount ?: 0,
    createdAt = createdAt,
    ocrStatus = ocrStatus
)
