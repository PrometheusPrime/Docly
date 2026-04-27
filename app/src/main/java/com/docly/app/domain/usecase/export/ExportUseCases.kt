package com.docly.app.domain.usecase.export

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.model.ValidationResult
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.StorageReserveBytes
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import java.io.File
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class GenerateDocumentNameUseCase @Inject constructor() {
    operator fun invoke(metadata: DocumentMetadata): String {
        val baseName = listOf(
            metadata.grade,
            metadata.subject,
            metadata.year.toString(),
            metadata.paperType,
            metadata.paperNumber.orEmpty()
        ).filter { it.isNotBlank() }
            .joinToString(separator = "_") { it.toSafeFilePart() }
            .ifBlank { "document" }

        return "$baseName.pdf"
    }
}

class ValidateMetadataUseCase @Inject constructor(private val timeProvider: TimeProvider) {
    operator fun invoke(metadata: DocumentMetadata): ValidationResult {
        val currentYear = Calendar.getInstance().apply {
            timeInMillis = timeProvider.now()
        }.get(Calendar.YEAR)
        val validYearRange = MIN_METADATA_YEAR..currentYear + FUTURE_YEAR_ALLOWANCE
        val errors = buildList {
            if (metadata.grade.isBlank()) add("Grade is required.")
            if (metadata.subject.isBlank()) add("Subject is required.")
            if (metadata.paperType.isBlank()) add("Paper type is required.")
            if (metadata.year !in validYearRange) {
                add("Year must be between $MIN_METADATA_YEAR and ${currentYear + FUTURE_YEAR_ALLOWANCE}.")
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    private companion object {
        const val MIN_METADATA_YEAR = 1980
        const val FUTURE_YEAR_ALLOWANCE = 1
    }
}

class GeneratePdfUseCase @Inject constructor(
    private val pdfRepository: PdfRepository,
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(fileName: String, pages: List<ScannedPage>): AppResult<String> {
        if (pages.isEmpty()) {
            return AppResult.Error(
                message = "At least one page is required before export.",
                category = AppErrorCategory.VALIDATION
            )
        }

        val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.EXPORT_BYTES)
        if (storageResult is AppResult.Error) {
            return storageResult
        }

        val pageImagePaths = pages.map { page -> page.processedImagePath ?: page.originalImagePath }
        return pdfRepository.createPdf(
            pageImagePaths = pageImagePaths,
            outputPdfPath = fileRepository.createPdfPath(fileName)
        )
    }
}

class SaveDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(document: SavedDocument): AppResult<Unit> = documentRepository.saveDocument(document)
}

class PrepareExportUseCase @Inject constructor(
    private val getScanSessionUseCase: GetScanSessionUseCase,
    private val generateDocumentNameUseCase: GenerateDocumentNameUseCase,
    private val validateMetadataUseCase: ValidateMetadataUseCase
) {
    suspend operator fun invoke(sessionId: String): AppResult<PreparedExport> {
        val normalizedSessionId = sessionId.trim()
        if (normalizedSessionId.isBlank()) {
            return validationError("Scan session not found.")
        }

        val session = when (val sessionResult = getScanSessionUseCase(normalizedSessionId)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data ?: return validationError("Scan session not found.")
        }
        val metadata = session.metadata ?: return validationError("Document details are required before export.")
        val metadataValidation = validateMetadataUseCase(metadata)
        if (!metadataValidation.isValid) {
            return validationError(metadataValidation.errors.firstOrNull() ?: "Document details are invalid.")
        }
        if (session.pages.any { page -> page.reviewStatus == PageReviewStatus.PENDING }) {
            return validationError("Review all pages before export.")
        }

        val exportPages = session.pages
            .filter { page -> page.reviewStatus == PageReviewStatus.ACCEPTED }
            .sortedBy { page -> page.pageIndex }
        if (exportPages.isEmpty()) {
            return validationError("At least one reviewed page is required before export.")
        }

        val unprocessedPage = exportPages.firstOrNull { page -> page.processedImagePath.isNullOrBlank() }
        if (unprocessedPage != null) {
            return validationError("Process every page before export.")
        }

        val missingImagePage = exportPages.firstOrNull { page -> !File(page.processedImagePath.orEmpty()).isFile }
        if (missingImagePage != null) {
            return validationError("Processed page image is missing. Reprocess the page before export.")
        }

        return AppResult.Success(
            PreparedExport(
                session = session,
                metadata = metadata,
                pages = exportPages,
                fileName = generateDocumentNameUseCase(metadata)
            )
        )
    }
}

class ExportDocumentUseCase @Inject constructor(
    private val prepareExportUseCase: PrepareExportUseCase,
    private val generatePdfUseCase: GeneratePdfUseCase,
    private val saveDocumentUseCase: SaveDocumentUseCase,
    private val updateScanSessionStatusUseCase: UpdateScanSessionStatusUseCase,
    private val deleteSavedDocumentUseCase: DeleteSavedDocumentUseCase,
    private val fileRepository: FileRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(sessionId: String): AppResult<ExportDocumentResult> {
        val preparedExport = when (val preparedResult = prepareExportUseCase(sessionId)) {
            is AppResult.Error -> return preparedResult
            is AppResult.Success -> preparedResult.data
        }

        val pdfPath = when (val pdfResult = generatePdfUseCase(preparedExport.fileName, preparedExport.pages)) {
            is AppResult.Error -> return pdfResult
            is AppResult.Success -> pdfResult.data
        }

        val document = preparedExport.toSavedDocument(pdfPath = pdfPath)
        when (val saveResult = saveDocumentUseCase(document)) {
            is AppResult.Error -> {
                fileRepository.deleteFile(pdfPath)
                return saveResult
            }

            is AppResult.Success -> Unit
        }

        return when (
            val statusResult = updateScanSessionStatusUseCase(
                sessionId = preparedExport.session.id,
                status = ScanSessionStatus.EXPORTED
            )
        ) {
            is AppResult.Error -> {
                deleteSavedDocumentUseCase(document.id)
                fileRepository.deleteFile(pdfPath)
                statusResult
            }

            is AppResult.Success -> AppResult.Success(ExportDocumentResult(document = document))
        }
    }

    private fun PreparedExport.toSavedDocument(pdfPath: String): SavedDocument = SavedDocument(
        id = idProvider.generateId(),
        sessionId = session.id,
        title = fileName.removeSuffix(PDF_EXTENSION),
        pdfPath = pdfPath,
        thumbnailPath = pages.firstNotNullOfOrNull { page -> page.thumbnailPath?.takeIf { it.isNotBlank() } },
        metadata = metadata,
        pageCount = pages.size,
        createdAt = timeProvider.now()
    )

    private companion object {
        const val PDF_EXTENSION = ".pdf"
    }
}

data class PreparedExport(
    val session: ScanSession,
    val metadata: DocumentMetadata,
    val pages: List<ScannedPage>,
    val fileName: String
)

data class ExportDocumentResult(val document: SavedDocument)

private fun validationError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.VALIDATION
)

private fun String.toSafeFilePart(): String = trim()
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9._-]+"), "_")
    .trim('_')
