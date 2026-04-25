package com.docly.app.data.repository

import com.docly.app.core.image.DocumentDetector
import com.docly.app.core.image.ImageEnhancer
import com.docly.app.core.image.PerspectiveTransformer
import com.docly.app.core.image.ThumbnailGenerator
import com.docly.app.core.image.WarpResult
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.errorOrNull
import com.docly.app.core.result.getOrNull
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ScanMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageProcessingRepositoryImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun processPageWarpsEnhancesThenGeneratesThumbnailForSelectedMode() = runBlocking {
        val perspectiveTransformer = FakePerspectiveTransformer()
        val imageEnhancer = FakeImageEnhancer()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val processedOutput = temporaryFolder.newFile("processed.jpg").apply { delete() }
        val thumbnailOutput = temporaryFolder.newFile("thumb.jpg").apply { delete() }

        val result = repository(
            perspectiveTransformer = perspectiveTransformer,
            imageEnhancer = imageEnhancer,
            thumbnailGenerator = thumbnailGenerator
        ).processPage(
            inputPath = "/raw/page.jpg",
            processedOutputPath = processedOutput.absolutePath,
            thumbnailOutputPath = thumbnailOutput.absolutePath,
            scanMode = ScanMode.MIXED,
            corners = sampleCorners()
        )

        val processedPage = result.getOrNull()
        val expectedWarpPath = File(processedOutput.parentFile, "processed_warp_tmp.jpg").absolutePath
        assertEquals("/raw/page.jpg", perspectiveTransformer.requests.single().imagePath)
        assertEquals(expectedWarpPath, perspectiveTransformer.requests.single().outputPath)
        assertEquals(expectedWarpPath, imageEnhancer.requests.single().inputPath)
        assertEquals(processedOutput.absolutePath, imageEnhancer.requests.single().outputPath)
        assertEquals(ScanMode.MIXED, imageEnhancer.requests.single().scanMode)
        assertEquals(processedOutput.absolutePath, thumbnailGenerator.requests.single().inputPath)
        assertEquals(thumbnailOutput.absolutePath, thumbnailGenerator.requests.single().outputPath)
        assertEquals(processedOutput.absolutePath, processedPage?.processedImagePath)
        assertEquals(thumbnailOutput.absolutePath, processedPage?.thumbnailPath)
        assertEquals(320, processedPage?.width)
        assertEquals(480, processedPage?.height)
        assertFalse(File(expectedWarpPath).exists())
        assertTrue(processedOutput.isFile)
        assertTrue(thumbnailOutput.isFile)
    }

    @Test
    fun warpFailureDeletesTemporaryPathAndSkipsEnhancement() = runBlocking {
        val perspectiveTransformer = FakePerspectiveTransformer(
            result = AppResult.Error(
                message = "Warp failed.",
                category = AppErrorCategory.PROCESSING
            )
        )
        val imageEnhancer = FakeImageEnhancer()
        val thumbnailGenerator = FakeThumbnailGenerator()
        val processedOutput = File(temporaryFolder.root, "processed.jpg")
        val thumbnailOutput = File(temporaryFolder.root, "thumb.jpg")

        val result = repository(
            perspectiveTransformer = perspectiveTransformer,
            imageEnhancer = imageEnhancer,
            thumbnailGenerator = thumbnailGenerator
        ).processPage(
            inputPath = "/raw/page.jpg",
            processedOutputPath = processedOutput.absolutePath,
            thumbnailOutputPath = thumbnailOutput.absolutePath,
            scanMode = ScanMode.DOCUMENT,
            corners = sampleCorners()
        )

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertTrue(imageEnhancer.requests.isEmpty())
        assertTrue(thumbnailGenerator.requests.isEmpty())
        assertFalse(File(temporaryFolder.root, "processed_warp_tmp.jpg").exists())
        assertFalse(processedOutput.exists())
        assertFalse(thumbnailOutput.exists())
    }

    @Test
    fun enhancerFailureDeletesGeneratedPathsAndSkipsThumbnail() = runBlocking {
        val imageEnhancer = FakeImageEnhancer(
            result = AppResult.Error(
                message = "Enhancement failed.",
                category = AppErrorCategory.PROCESSING
            ),
            writeOutputBeforeResult = true
        )
        val thumbnailGenerator = FakeThumbnailGenerator()
        val processedOutput = File(temporaryFolder.root, "processed.jpg")
        val thumbnailOutput = File(temporaryFolder.root, "thumb.jpg")

        val result = repository(
            imageEnhancer = imageEnhancer,
            thumbnailGenerator = thumbnailGenerator
        ).processPage(
            inputPath = "/raw/page.jpg",
            processedOutputPath = processedOutput.absolutePath,
            thumbnailOutputPath = thumbnailOutput.absolutePath,
            scanMode = ScanMode.COLOR,
            corners = sampleCorners()
        )

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertEquals(ScanMode.COLOR, imageEnhancer.requests.single().scanMode)
        assertTrue(thumbnailGenerator.requests.isEmpty())
        assertFalse(File(temporaryFolder.root, "processed_warp_tmp.jpg").exists())
        assertFalse(processedOutput.exists())
        assertFalse(thumbnailOutput.exists())
    }

    @Test
    fun thumbnailFailureDeletesEnhancedAndThumbnailOutputs() = runBlocking {
        val thumbnailGenerator = FakeThumbnailGenerator(
            result = AppResult.Error(
                message = "Thumbnail failed.",
                category = AppErrorCategory.PROCESSING
            ),
            writeOutputBeforeResult = true
        )
        val processedOutput = File(temporaryFolder.root, "processed.jpg")
        val thumbnailOutput = File(temporaryFolder.root, "thumb.jpg")

        val result = repository(thumbnailGenerator = thumbnailGenerator).processPage(
            inputPath = "/raw/page.jpg",
            processedOutputPath = processedOutput.absolutePath,
            thumbnailOutputPath = thumbnailOutput.absolutePath,
            scanMode = ScanMode.DOCUMENT,
            corners = sampleCorners()
        )

        assertEquals(AppErrorCategory.PROCESSING, result.errorOrNull()?.category)
        assertFalse(File(temporaryFolder.root, "processed_warp_tmp.jpg").exists())
        assertFalse(processedOutput.exists())
        assertFalse(thumbnailOutput.exists())
    }

    private fun repository(
        perspectiveTransformer: PerspectiveTransformer = FakePerspectiveTransformer(),
        imageEnhancer: ImageEnhancer = FakeImageEnhancer(),
        thumbnailGenerator: ThumbnailGenerator = FakeThumbnailGenerator()
    ): ImageProcessingRepositoryImpl = ImageProcessingRepositoryImpl(
        documentDetector = FakeDocumentDetector(),
        perspectiveTransformer = perspectiveTransformer,
        imageEnhancer = imageEnhancer,
        thumbnailGenerator = thumbnailGenerator
    )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 30f),
        topRight = PointFSerializable(900f, 40f),
        bottomRight = PointFSerializable(880f, 1300f),
        bottomLeft = PointFSerializable(30f, 1290f)
    )

    private class FakeDocumentDetector : DocumentDetector {
        override suspend fun detect(imagePath: String): AppResult<PageCorners?> = AppResult.Success(null)
    }

    private class FakePerspectiveTransformer(private val result: AppResult<WarpResult>? = null) :
        PerspectiveTransformer {
        val requests: MutableList<WarpRequest> = mutableListOf()

        override suspend fun warp(imagePath: String, corners: PageCorners, outputPath: String): AppResult<WarpResult> {
            requests += WarpRequest(imagePath = imagePath, corners = corners, outputPath = outputPath)
            File(outputPath).writeText("warp")
            return result ?: AppResult.Success(WarpResult(outputPath = outputPath, width = 320, height = 480))
        }
    }

    private class FakeImageEnhancer(
        private val result: AppResult<String>? = null,
        private val writeOutputBeforeResult: Boolean = false
    ) : ImageEnhancer {
        val requests: MutableList<EnhanceRequest> = mutableListOf()

        override suspend fun enhance(inputPath: String, outputPath: String, scanMode: ScanMode): AppResult<String> {
            requests += EnhanceRequest(inputPath = inputPath, outputPath = outputPath, scanMode = scanMode)
            if (result == null || writeOutputBeforeResult) {
                File(outputPath).writeText("enhanced")
            }
            return result ?: AppResult.Success(outputPath)
        }
    }

    private class FakeThumbnailGenerator(
        private val result: AppResult<String>? = null,
        private val writeOutputBeforeResult: Boolean = false
    ) : ThumbnailGenerator {
        val requests: MutableList<ThumbnailRequest> = mutableListOf()

        override suspend fun generate(inputPath: String, outputPath: String): AppResult<String> {
            requests += ThumbnailRequest(inputPath = inputPath, outputPath = outputPath)
            if (result == null || writeOutputBeforeResult) {
                File(outputPath).writeText("thumbnail")
            }
            return result ?: AppResult.Success(outputPath)
        }
    }

    private data class WarpRequest(val imagePath: String, val corners: PageCorners, val outputPath: String)

    private data class EnhanceRequest(val inputPath: String, val outputPath: String, val scanMode: ScanMode)

    private data class ThumbnailRequest(val inputPath: String, val outputPath: String)
}
