package com.docly.app.data.repository

import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.ImageEnhancer
import com.docly.app.core.image.PerspectiveTransformer
import com.docly.app.core.image.ThumbnailGenerator
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.repository.ImageProcessingRepository
import javax.inject.Inject

class ImageProcessingRepositoryImpl @Inject constructor(
    private val documentDetector: DocumentDetector,
    private val perspectiveTransformer: PerspectiveTransformer,
    private val imageEnhancer: ImageEnhancer,
    private val thumbnailGenerator: ThumbnailGenerator
) : ImageProcessingRepository {
    override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = documentDetector.detect(inputPath)

    override suspend fun processPage(
        inputPath: String,
        outputPath: String,
        scanMode: ScanMode,
        corners: PageCorners?
    ): AppResult<ProcessedPageResult> = AppResult.Error(
        message = "Image processing pipeline is not implemented yet.",
        category = AppErrorCategory.PROCESSING
    )

    override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
        thumbnailGenerator.generate(inputPath = inputPath, outputPath = outputPath)
}
