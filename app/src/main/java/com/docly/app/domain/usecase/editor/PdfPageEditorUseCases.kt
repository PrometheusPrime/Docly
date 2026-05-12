package com.docly.app.domain.usecase.editor

import com.docly.app.core.image.ScanPageRenderer
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

open class LoadScannedPdfPageEditorUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scanRepository: ScanRepository
) {
    open suspend operator fun invoke(documentId: String): AppResult<ScannedPdfPageEditorDocument> {
        val document = when (val documentResult = documentRepository.getDocument(documentId.trim())) {
            is AppResult.Error -> return documentResult
            is AppResult.Success -> documentResult.data ?: return validationError("Document not found.")
        }
        val sessionId = when (val validationResult = document.requireSourceScanSessionId()) {
            is AppResult.Error -> return validationResult
            is AppResult.Success -> validationResult.data
        }
        val session = when (val sessionResult = scanRepository.getSession(sessionId)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data ?: return validationError("Source scan session was not found.")
        }
        val pages = session.pages
            .filter { page -> page.reviewStatus == PageReviewStatus.ACCEPTED }
            .sortedBy { page -> page.pageIndex }
        if (pages.isEmpty()) {
            return validationError("This PDF has no retained scan pages to edit.")
        }

        return AppResult.Success(ScannedPdfPageEditorDocument(document = document, pages = pages))
    }
}

open class SaveScannedPdfPageEditsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scanRepository: ScanRepository,
    private val pdfRepository: PdfRepository,
    private val fileRepository: FileRepository,
    private val scanPageRenderer: ScanPageRenderer,
    private val timeProvider: TimeProvider
) {
    open suspend operator fun invoke(
        documentId: String,
        editedPages: List<ScannedPage>
    ): AppResult<ScannedPdfPageEditorDocument> {
        if (editedPages.isEmpty()) {
            return validationError("Keep at least one page in this PDF.")
        }

        val document = when (val documentResult = documentRepository.getDocument(documentId.trim())) {
            is AppResult.Error -> return documentResult
            is AppResult.Success -> documentResult.data ?: return validationError("Document not found.")
        }
        val sessionId = when (val validationResult = document.requireSourceScanSessionId()) {
            is AppResult.Error -> return validationResult
            is AppResult.Success -> validationResult.data
        }
        val pdfPath = when (val pathResult = document.requireInternalPdfPath()) {
            is AppResult.Error -> return pathResult
            is AppResult.Success -> pathResult.data
        }
        val sourceSession = when (val sessionResult = scanRepository.getSession(sessionId)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data ?: return validationError("Source scan session was not found.")
        }
        val sourcePages = sourceSession.pages
            .filter { page -> page.reviewStatus == PageReviewStatus.ACCEPTED }
            .sortedBy { page -> page.pageIndex }
        val sourcePageIds = sourcePages.map { page -> page.id }
        val editedPageIds = editedPages.map { page -> page.id }
        if (editedPageIds.distinct().size != editedPageIds.size || !sourcePageIds.containsAll(editedPageIds)) {
            return validationError("Edited pages must come from this PDF's retained scan source.")
        }

        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.EXPORT_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        val tempImagePaths = mutableListOf<String>()
        val tempPdfPath = "$pdfPath.phase6.tmp"
        return try {
            val pageImagePaths = when (val renderedResult = renderPageImages(editedPages, tempImagePaths, sessionId)) {
                is AppResult.Error -> return renderedResult
                is AppResult.Success -> renderedResult.data
            }
            when (
                val pdfResult = pdfRepository.createPdf(
                    pageImagePaths = pageImagePaths,
                    outputPdfPath = tempPdfPath
                )
            ) {
                is AppResult.Error -> return pdfResult
                is AppResult.Success -> Unit
            }

            val deletedPageIds = sourcePageIds.filterNot { pageId -> pageId in editedPageIds }
            for (deletedPageId in deletedPageIds) {
                when (val deleteResult = scanRepository.deletePage(deletedPageId)) {
                    is AppResult.Error -> return deleteResult
                    is AppResult.Success -> Unit
                }
            }
            editedPages.forEachIndexed { index, page ->
                val updatedPage = page.copy(pageIndex = index)
                when (val updateResult = scanRepository.updatePage(updatedPage)) {
                    is AppResult.Error -> return updateResult
                    is AppResult.Success -> Unit
                }
            }
            when (val reorderResult = scanRepository.reorderPages(sessionId, editedPageIds)) {
                is AppResult.Error -> return reorderResult
                is AppResult.Success -> Unit
            }

            replaceFile(sourcePath = tempPdfPath, targetPath = pdfPath)
            val pdfFile = File(pdfPath)
            val updatedDocument = document.copy(
                fileSize = pdfFile.length(),
                pageCount = editedPages.size,
                thumbnailPath = editedPages.firstNotNullOfOrNull { page ->
                    page.thumbnailPath?.takeIf { thumbnailPath -> thumbnailPath.isNotBlank() }
                },
                updatedAt = timeProvider.now()
            )
            when (val saveResult = documentRepository.upsertDocument(updatedDocument)) {
                is AppResult.Error -> return saveResult
                is AppResult.Success -> Unit
            }
            AppResult.Success(
                ScannedPdfPageEditorDocument(
                    document = updatedDocument,
                    pages = editedPages.mapIndexed { index, page -> page.copy(pageIndex = index) }
                )
            )
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Could not update this PDF.",
                category = AppErrorCategory.PDF,
                throwable = throwable
            )
        } finally {
            fileRepository.deleteFiles(tempImagePaths + tempPdfPath)
        }
    }

    private suspend fun renderPageImages(
        pages: List<ScannedPage>,
        tempImagePaths: MutableList<String>,
        sessionId: String
    ): AppResult<List<String>> {
        val paths = mutableListOf<String>()
        pages.forEachIndexed { index, page ->
            val sourcePath = page.processedImagePath ?: page.originalImagePath
            if (!File(sourcePath).isFile) {
                return AppResult.Error(
                    message = "Scanned page image is missing.",
                    category = AppErrorCategory.STORAGE
                )
            }
            if (page.rotationDegrees.normalizedRotation() == 0) {
                paths += sourcePath
            } else {
                val tempPath = fileRepository.createTempImagePath("${sessionId}_page_edit_$index")
                tempImagePaths += tempPath
                when (val renderResult = scanPageRenderer.render(page = page, outputPath = tempPath)) {
                    is AppResult.Error -> return renderResult
                    is AppResult.Success -> paths += renderResult.data
                }
            }
        }
        return AppResult.Success(paths)
    }

    private fun replaceFile(sourcePath: String, targetPath: String) {
        val source = File(sourcePath)
        val target = File(targetPath)
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (atomicMoveFailure: Throwable) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

data class ScannedPdfPageEditorDocument(val document: DoclyDocument, val pages: List<ScannedPage>)

private fun DoclyDocument.requireSourceScanSessionId(): AppResult<String> {
    if (type != DocumentType.PDF) {
        return validationError("Only PDFs can use page tools.")
    }
    return sourceScanSessionId
        ?.takeIf { sessionId -> sessionId.isNotBlank() }
        ?.let { sessionId -> AppResult.Success(sessionId) }
        ?: validationError("Only Docly-created scan PDFs can use page tools.")
}

private fun DoclyDocument.requireInternalPdfPath(): AppResult<String> {
    val internalFile = fileRef as? FileRef.InternalFile
        ?: return validationError("Only local PDFs can use page tools.")
    return AppResult.Success(internalFile.path)
}

private fun Int.normalizedRotation(): Int =
    ((this % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES

private fun validationError(message: String): AppResult.Error = AppResult.Error(
    message = message,
    category = AppErrorCategory.VALIDATION
)

private const val FULL_ROTATION_DEGREES = 360
