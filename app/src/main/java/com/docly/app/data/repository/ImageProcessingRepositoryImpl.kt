package com.docly.app.data.repository

import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.PerspectiveTransformer
import com.docly.app.core.image.ThumbnailGenerator
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.repository.ImageProcessingRepository
import java.io.File
import javax.inject.Inject

class ImageProcessingRepositoryImpl @Inject constructor(
    private val documentDetector: DocumentDetector,
    private val perspectiveTransformer: PerspectiveTransformer,
    private val thumbnailGenerator: ThumbnailGenerator
) : ImageProcessingRepository {
    override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = documentDetector.detect(inputPath)

    override suspend fun processPage(
        inputPath: String,
        processedOutputPath: String,
        thumbnailOutputPath: String,
        scanMode: ScanMode,
        corners: PageCorners?
    ): AppResult<ProcessedPageResult> {
        if (corners == null) {
            return AppResult.Error(
                message = "Page corners are required for crop processing.",
                category = AppErrorCategory.PROCESSING
            )
        }

        val warpResult = when (
            val result = perspectiveTransformer.warp(
                imagePath = inputPath,
                corners = corners,
                outputPath = processedOutputPath
            )
        ) {
            is AppResult.Error -> return result
            is AppResult.Success -> result.data
        }

        val thumbnailPath = when (
            val result = thumbnailGenerator.generate(
                inputPath = warpResult.outputPath,
                outputPath = thumbnailOutputPath
            )
        ) {
            is AppResult.Error -> {
                File(warpResult.outputPath).delete()
                return result
            }

            is AppResult.Success -> result.data
        }

        return AppResult.Success(
            ProcessedPageResult(
                processedImagePath = warpResult.outputPath,
                thumbnailPath = thumbnailPath,
                detectedCorners = corners,
                width = warpResult.width,
                height = warpResult.height
            )
        )
    }

    override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
        thumbnailGenerator.generate(inputPath = inputPath, outputPath = outputPath)
}
