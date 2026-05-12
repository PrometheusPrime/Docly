package com.docly.app.domain.usecase.scanner

import com.docly.app.core.common.IdProvider
import com.docly.app.core.image.ScanPageRenderer
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import java.io.File
import javax.inject.Inject

class ImportScannedPagesUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val devicePhotoRepository: DevicePhotoRepository,
    private val fileRepository: FileRepository,
    private val imageProcessingRepository: ImageProcessingRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        sessionId: String?,
        pageImageUris: List<String>,
        scanMode: ScanMode = ScanMode.DOCUMENT
    ): AppResult<ImportScannedPagesResult> {
        val normalizedUris = pageImageUris
            .map { uri -> uri.trim() }
            .filter { uri -> uri.isNotBlank() }
        if (normalizedUris.isEmpty()) {
            return validationError("No scanned pages were returned.")
        }

        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.CAPTURE_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        val session = when (val sessionResult = resolveSession(sessionId = sessionId, scanMode = scanMode)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data
        }
        val importedPages = mutableListOf<ScannedPage>()
        val firstPageIndex = session.pages.maxOfOrNull { page -> page.pageIndex }?.plus(1) ?: 0

        normalizedUris.forEachIndexed { offset, sourceUri ->
            val pageId = idProvider.generateId()
            val importedRawImage = when (
                val importResult = devicePhotoRepository.importRawPhoto(
                    sessionId = session.id,
                    pageId = pageId,
                    sourceUri = sourceUri
                )
            ) {
                is AppResult.Error -> {
                    rollbackImportedPages(importedPages)
                    return importResult
                }

                is AppResult.Success -> importResult.data
            }

            val thumbnailPath = fileRepository.createThumbnailPath(sessionId = session.id, suffix = pageId)
            val generatedThumbnailPath = when (
                val thumbnailResult = imageProcessingRepository.generateThumbnail(
                    inputPath = importedRawImage.path,
                    outputPath = thumbnailPath
                )
            ) {
                is AppResult.Error -> {
                    cleanupImportedRawImage(importedRawImage = importedRawImage, thumbnailPath = thumbnailPath)
                    rollbackImportedPages(importedPages)
                    return thumbnailResult
                }

                is AppResult.Success -> thumbnailResult.data
            }

            val page = ScannedPage(
                id = pageId,
                sessionId = session.id,
                pageIndex = firstPageIndex + offset,
                originalImagePath = importedRawImage.path,
                processedImagePath = importedRawImage.path,
                thumbnailPath = generatedThumbnailPath,
                rotationDegrees = 0,
                scanMode = scanMode,
                width = importedRawImage.width,
                height = importedRawImage.height,
                corners = null,
                createdAt = timeProvider.now(),
                reviewStatus = PageReviewStatus.ACCEPTED
            )

            when (val addPageResult = scanRepository.addPage(page)) {
                is AppResult.Error -> {
                    fileRepository.deletePageAssets(page)
                    rollbackImportedPages(importedPages)
                    return addPageResult
                }

                is AppResult.Success -> importedPages += page
            }
        }

        return AppResult.Success(
            ImportScannedPagesResult(
                sessionId = session.id,
                importedPages = importedPages
            )
        )
    }

    private suspend fun resolveSession(sessionId: String?, scanMode: ScanMode): AppResult<ScanSession> {
        if (!sessionId.isNullOrBlank()) {
            return when (val sessionResult = scanRepository.getSession(sessionId)) {
                is AppResult.Error -> sessionResult

                is AppResult.Success -> {
                    val session = sessionResult.data
                    if (session?.status == ScanSessionStatus.IN_PROGRESS) {
                        AppResult.Success(session)
                    } else {
                        validationError("Scan session not found.")
                    }
                }
            }
        }

        return scanRepository.createSession(scanMode)
    }

    private suspend fun cleanupImportedRawImage(importedRawImage: ImportedRawImage, thumbnailPath: String) {
        fileRepository.deleteFiles(listOf(importedRawImage.path, thumbnailPath))
    }

    private suspend fun rollbackImportedPages(importedPages: List<ScannedPage>) {
        importedPages.asReversed().forEach { page ->
            scanRepository.deletePage(page.id)
        }
    }
}

class SaveScannedOutputUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val documentRepository: DocumentRepository,
    private val pdfRepository: PdfRepository,
    private val fileRepository: FileRepository,
    private val scanPageRenderer: ScanPageRenderer,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        sessionId: String,
        title: String,
        outputFormat: ScannedOutputFormat
    ): AppResult<SaveScannedOutputResult> {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return validationError("Document title is required.")
        }

        val session = when (val sessionResult = scanRepository.getSession(sessionId.trim())) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data ?: return validationError("Scan session not found.")
        }
        val pages = session.pages.sortedBy { page -> page.pageIndex }
        if (pages.isEmpty()) {
            return validationError("At least one scanned page is required.")
        }

        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.EXPORT_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        return when (outputFormat) {
            ScannedOutputFormat.PDF -> savePdf(session = session, title = normalizedTitle, pages = pages)
            ScannedOutputFormat.IMAGES -> saveImages(session = session, title = normalizedTitle, pages = pages)
        }
    }

    private suspend fun savePdf(
        session: ScanSession,
        title: String,
        pages: List<ScannedPage>
    ): AppResult<SaveScannedOutputResult> {
        val tempPaths = mutableListOf<String>()
        val pdfPath = fileRepository.createPdfPath(title)
        val documentsToRollback = mutableListOf<DoclyDocument>()

        return try {
            val pageImagePaths = try {
                pages.mapIndexed { index, page ->
                    page.renderedPathIfNeeded(tempPaths = tempPaths, suffix = "${session.id}_pdf_$index")
                }
            } catch (failure: SaveScannedOutputFailure) {
                return failure.error
            }
            pdfRepository.createPdf(pageImagePaths = pageImagePaths, outputPdfPath = pdfPath).let { result ->
                if (result is AppResult.Error) return result
            }

            val document = createDocument(
                name = title,
                type = DocumentType.PDF,
                mimeType = PDF_MIME_TYPE,
                filePath = pdfPath,
                pageCount = pages.size
            )
            when (val saveResult = documentRepository.upsertDocument(document)) {
                is AppResult.Error -> {
                    fileRepository.deleteFile(pdfPath)
                    saveResult
                }

                is AppResult.Success -> {
                    documentsToRollback += document
                    when (
                        val statusResult = scanRepository.updateSessionStatus(
                            session.id,
                            ScanSessionStatus.EXPORTED
                        )
                    ) {
                        is AppResult.Error -> {
                            rollbackDocuments(documentsToRollback)
                            fileRepository.deleteFile(pdfPath)
                            statusResult
                        }

                        is AppResult.Success -> AppResult.Success(SaveScannedOutputResult(listOf(document)))
                    }
                }
            }
        } finally {
            fileRepository.deleteFiles(tempPaths)
        }
    }

    private suspend fun saveImages(
        session: ScanSession,
        title: String,
        pages: List<ScannedPage>
    ): AppResult<SaveScannedOutputResult> {
        val savedDocuments = mutableListOf<DoclyDocument>()
        val createdPaths = mutableListOf<String>()

        pages.forEachIndexed { index, page ->
            val pageTitle = if (pages.size == 1) title else "$title Page ${index + 1}"
            val imagePath = fileRepository.createImageDocumentPath(pageTitle)
            val renderResult = page.renderToOutput(imagePath)
            if (renderResult is AppResult.Error) {
                rollbackDocuments(savedDocuments)
                fileRepository.deleteFiles(createdPaths + imagePath)
                return renderResult
            }
            createdPaths += imagePath

            val document = createDocument(
                name = pageTitle,
                type = DocumentType.IMAGE,
                mimeType = IMAGE_MIME_TYPE,
                filePath = imagePath,
                pageCount = 1,
                thumbnailPath = imagePath
            )
            when (val saveResult = documentRepository.upsertDocument(document)) {
                is AppResult.Error -> {
                    rollbackDocuments(savedDocuments)
                    fileRepository.deleteFiles(createdPaths)
                    return saveResult
                }

                is AppResult.Success -> savedDocuments += document
            }
        }

        return when (val statusResult = scanRepository.updateSessionStatus(session.id, ScanSessionStatus.EXPORTED)) {
            is AppResult.Error -> {
                rollbackDocuments(savedDocuments)
                fileRepository.deleteFiles(createdPaths)
                statusResult
            }

            is AppResult.Success -> AppResult.Success(SaveScannedOutputResult(savedDocuments))
        }
    }

    private suspend fun ScannedPage.renderedPathIfNeeded(tempPaths: MutableList<String>, suffix: String): String {
        val sourcePath = processedImagePath ?: originalImagePath
        if (rotationDegrees.normalizedRotation() == 0) {
            return sourcePath
        }

        val tempPath = fileRepository.createTempImagePath(suffix)
        tempPaths += tempPath
        when (val renderResult = scanPageRenderer.render(page = this, outputPath = tempPath)) {
            is AppResult.Error -> throw SaveScannedOutputFailure(renderResult)
            is AppResult.Success -> return renderResult.data
        }
    }

    private suspend fun ScannedPage.renderToOutput(outputPath: String): AppResult<String> =
        if (rotationDegrees.normalizedRotation() == 0) {
            copyPageImageTo(outputPath)
        } else {
            scanPageRenderer.render(page = this, outputPath = outputPath)
        }

    private fun ScannedPage.copyPageImageTo(outputPath: String): AppResult<String> {
        return try {
            val sourceFile = File(processedImagePath ?: originalImagePath)
            if (!sourceFile.isFile) {
                return AppResult.Error(
                    message = "Scanned page image is missing.",
                    category = AppErrorCategory.STORAGE
                )
            }

            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            sourceFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            AppResult.Success(outputPath)
        } catch (throwable: Throwable) {
            File(outputPath).delete()
            AppResult.Error(
                message = "Scanned page image could not be saved.",
                category = AppErrorCategory.STORAGE,
                throwable = throwable
            )
        }
    }

    private fun createDocument(
        name: String,
        type: DocumentType,
        mimeType: String,
        filePath: String,
        pageCount: Int,
        thumbnailPath: String? = null
    ): DoclyDocument {
        val now = timeProvider.now()
        return DoclyDocument(
            id = idProvider.generateId(),
            name = name,
            type = type,
            mimeType = mimeType,
            fileRef = FileRef.InternalFile(filePath),
            source = DocumentSource.SCANNED,
            folderId = null,
            thumbnailPath = thumbnailPath,
            fileSize = File(filePath).length(),
            pageCount = pageCount,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = null,
            isFavorite = false,
            isScanned = true,
            ocrStatus = OcrStatus.NOT_STARTED
        )
    }

    private suspend fun rollbackDocuments(documents: List<DoclyDocument>) {
        documents.asReversed().forEach { document ->
            documentRepository.deleteDocument(document.id)
        }
    }

    private fun Int.normalizedRotation(): Int = ((this % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) %
        FULL_ROTATION_DEGREES

    private companion object {
        const val FULL_ROTATION_DEGREES = 360
        const val PDF_MIME_TYPE = "application/pdf"
        const val IMAGE_MIME_TYPE = "image/jpeg"
    }
}

enum class ScannedOutputFormat {
    PDF,
    IMAGES
}

data class ImportScannedPagesResult(val sessionId: String, val importedPages: List<ScannedPage>)

data class SaveScannedOutputResult(val documents: List<DoclyDocument>)

private class SaveScannedOutputFailure(val error: AppResult.Error) : RuntimeException(error.message)

private fun validationError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.VALIDATION
)
