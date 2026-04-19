package com.docly.app.domain.usecase.export

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.model.ValidationResult
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.StorageReserveBytes
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

private fun String.toSafeFilePart(): String = trim()
    .lowercase(Locale.US)
    .replace(Regex("[^a-z0-9._-]+"), "_")
    .trim('_')
