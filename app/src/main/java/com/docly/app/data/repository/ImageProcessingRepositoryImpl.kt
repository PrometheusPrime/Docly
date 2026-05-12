package com.docly.app.data.repository

import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.ImageEnhancer
import com.docly.app.core.image.PerspectiveTransformer
import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.image.ScanQualityEvaluator
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
    private val imageEnhancer: ImageEnhancer,
    private val scanQualityEvaluator: ScanQualityEvaluator,
    private val thumbnailGenerator: ThumbnailGenerator
) : ImageProcessingRepository {
    override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = documentDetector.detect(inputPath)

    override suspend fun evaluateQuality(inputPath: String, corners: PageCorners?): AppResult<ScanQualityAssessment> =
        scanQualityEvaluator.evaluate(imagePath = inputPath, corners = corners)

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

        val warpedOutputPath = processedOutputPath.toWarpTemporaryPath()
        val generatedPaths = listOf(warpedOutputPath, processedOutputPath, thumbnailOutputPath)
        val warpResult = when (
            val result = perspectiveTransformer.warp(
                imagePath = inputPath,
                corners = corners,
                outputPath = warpedOutputPath
            )
        ) {
            is AppResult.Error -> {
                generatedPaths.deleteFiles()
                return result
            }

            is AppResult.Success -> result.data
        }

        val enhancedPath = when (
            val result = imageEnhancer.enhance(
                inputPath = warpResult.outputPath,
                outputPath = processedOutputPath,
                scanMode = scanMode
            )
        ) {
            is AppResult.Error -> {
                generatedPaths.deleteFiles()
                return result
            }

            is AppResult.Success -> result.data
        }
        val generatedPathsAfterEnhancement = generatedPaths + enhancedPath
        File(warpResult.outputPath).delete()

        val thumbnailPath = when (
            val result = thumbnailGenerator.generate(
                inputPath = enhancedPath,
                outputPath = thumbnailOutputPath
            )
        ) {
            is AppResult.Error -> {
                generatedPathsAfterEnhancement.deleteFiles()
                return result
            }

            is AppResult.Success -> result.data
        }

        return AppResult.Success(
            ProcessedPageResult(
                processedImagePath = enhancedPath,
                thumbnailPath = thumbnailPath,
                detectedCorners = corners,
                width = warpResult.width,
                height = warpResult.height
            )
        )
    }

    override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
        thumbnailGenerator.generate(inputPath = inputPath, outputPath = outputPath)

    private fun String.toWarpTemporaryPath(): String {
        val file = File(this)
        val extension = file.extension.ifBlank { DEFAULT_PROCESSED_EXTENSION }
        val temporaryFileName = "${file.nameWithoutExtension}_warp_tmp.$extension"
        return (file.parentFile?.let { parent -> File(parent, temporaryFileName) } ?: File(temporaryFileName))
            .absolutePath
    }

    private fun List<String>.deleteFiles() {
        distinct()
            .filter { path -> path.isNotBlank() }
            .forEach { path -> File(path).delete() }
    }

    private companion object {
        const val DEFAULT_PROCESSED_EXTENSION = "jpg"
    }
}
