package com.docly.app.domain.usecase.library

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.usecase.reader.RenderPdfPageUseCase
import java.io.File
import javax.inject.Inject

class GenerateDocumentThumbnailUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val fileRepository: FileRepository,
    private val imageProcessingRepository: ImageProcessingRepository,
    private val renderPdfPageUseCase: RenderPdfPageUseCase
) {
    suspend operator fun invoke(documentId: String): AppResult<Unit> {
        val document = when (val result = documentRepository.getDocument(documentId)) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data ?: return AppResult.Success(Unit)
        }
        if (!document.thumbnailPath.isNullOrBlank() && File(document.thumbnailPath).isFile) {
            return AppResult.Success(Unit)
        }

        val sourcePath = when (val sourceResult = document.thumbnailSourcePath()) {
            is AppResult.Error -> return sourceResult
            is AppResult.Success -> sourceResult.data ?: return AppResult.Success(Unit)
        }
        val thumbnailPath = fileRepository.createThumbnailPath(sessionId = document.id, suffix = LIBRARY_SUFFIX)
        val generatedPath = when (
            val thumbnailResult = imageProcessingRepository.generateThumbnail(
                inputPath = sourcePath,
                outputPath = thumbnailPath
            )
        ) {
            is AppResult.Error -> return thumbnailResult
            is AppResult.Success -> thumbnailResult.data
        }

        return documentRepository.updateThumbnailPath(documentId = document.id, thumbnailPath = generatedPath)
    }

    private suspend fun DoclyDocument.thumbnailSourcePath(): AppResult<String?> = when (type) {
        DocumentType.IMAGE -> AppResult.Success((fileRef as? FileRef.InternalFile)?.path)

        DocumentType.PDF -> when (
            val renderResult = renderPdfPageUseCase(
                documentId = id,
                fileRef = fileRef,
                pageIndex = FIRST_PAGE_INDEX,
                widthPx = PDF_THUMBNAIL_RENDER_WIDTH_PX,
                zoom = 1f
            )
        ) {
            is AppResult.Error -> renderResult
            is AppResult.Success -> AppResult.Success(renderResult.data.imagePath)
        }

        DocumentType.TXT,
        DocumentType.MARKDOWN,
        DocumentType.HTML,
        DocumentType.DOCX,
        DocumentType.XLSX,
        DocumentType.CSV,
        DocumentType.UNKNOWN -> AppResult.Success(null)
    }

    private companion object {
        const val FIRST_PAGE_INDEX = 0
        const val PDF_THUMBNAIL_RENDER_WIDTH_PX = 512
        const val LIBRARY_SUFFIX = "library"
    }
}
