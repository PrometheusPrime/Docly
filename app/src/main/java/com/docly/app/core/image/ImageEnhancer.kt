package com.docly.app.core.image

import android.graphics.Bitmap
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ScanMode
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

interface ImageEnhancer {
    suspend fun enhance(inputPath: String, outputPath: String, scanMode: ScanMode): AppResult<String>
}

class OpenCvImageEnhancer @Inject constructor(
    private val openCvInitializer: OpenCvInitializer,
    private val bitmapLoader: BitmapLoader,
    private val dispatcherProvider: DispatcherProvider
) : ImageEnhancer {
    override suspend fun enhance(inputPath: String, outputPath: String, scanMode: ScanMode): AppResult<String> {
        if (inputPath.isBlank()) {
            return processingError("Image path is required for enhancement.")
        }
        if (outputPath.isBlank()) {
            return processingError("Output path is required for enhancement.")
        }

        when (val initializationResult = openCvInitializer.initialize()) {
            is AppResult.Error -> return initializationResult
            is AppResult.Success -> Unit
        }

        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = inputPath,
                maxWidth = ENHANCEMENT_MAX_LONG_EDGE_PX,
                maxHeight = ENHANCEMENT_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        var enhancedBitmap: Bitmap? = null
        return try {
            enhancedBitmap = withContext(dispatcherProvider.default) {
                decodedBitmap.enhanceToBitmap(scanMode)
            }
            withContext(dispatcherProvider.io) {
                enhancedBitmap?.writeJpeg(outputPath)
                    ?: throw IllegalStateException("Enhanced image was not created.")
            }
            AppResult.Success(outputPath)
        } catch (throwable: Throwable) {
            File(outputPath).delete()
            AppResult.Error(
                message = "Image enhancement failed.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            enhancedBitmap?.recycle()
            decodedBitmap.recycle()
        }
    }

    private fun Bitmap.enhanceToBitmap(scanMode: ScanMode): Bitmap {
        val source = Mat()
        var enhanced: Mat? = null
        var outputBitmap: Bitmap? = null

        try {
            Utils.bitmapToMat(this, source)
            enhanced = when (scanMode) {
                ScanMode.DOCUMENT -> source.enhanceDocument()
                ScanMode.MIXED -> source.enhanceMixed()
                ScanMode.COLOR -> source.enhanceColor()
            }
            outputBitmap = Bitmap.createBitmap(enhanced.width(), enhanced.height(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(enhanced, outputBitmap)
            return outputBitmap
        } catch (throwable: Throwable) {
            outputBitmap?.recycle()
            throw throwable
        } finally {
            source.release()
            enhanced?.release()
        }
    }

    private fun Mat.enhanceDocument(): Mat {
        val gray = toGray()
        val denoised = Mat()
        val thresholded = Mat()
        var sharpened: Mat? = null
        return try {
            Imgproc.medianBlur(gray, denoised, DOCUMENT_MEDIAN_KERNEL_SIZE)
            Imgproc.adaptiveThreshold(
                denoised,
                thresholded,
                MAX_BINARY_VALUE,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                DOCUMENT_THRESHOLD_BLOCK_SIZE,
                DOCUMENT_THRESHOLD_C
            )
            sharpened = thresholded.sharpen(amount = DOCUMENT_SHARPEN_AMOUNT)
            sharpened.toRgba()
        } finally {
            gray.release()
            denoised.release()
            thresholded.release()
            sharpened?.release()
        }
    }

    private fun Mat.enhanceMixed(): Mat {
        val rgb = toRgb()
        val denoised = Mat()
        val lab = Mat()
        val contrastRgb = Mat()
        val channels = mutableListOf<Mat>()
        var sharpened: Mat? = null
        return try {
            Imgproc.bilateralFilter(rgb, denoised, MIXED_BILATERAL_DIAMETER, MIXED_SIGMA, MIXED_SIGMA)
            Imgproc.cvtColor(denoised, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            val clahe = Imgproc.createCLAHE(MIXED_CLAHE_CLIP_LIMIT, Size(CLAHE_TILE_SIZE, CLAHE_TILE_SIZE))
            try {
                clahe.apply(channels.first(), channels.first())
            } finally {
                clahe.collectGarbage()
            }
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, contrastRgb, Imgproc.COLOR_Lab2RGB)
            sharpened = contrastRgb.sharpen(amount = MIXED_SHARPEN_AMOUNT)
            sharpened.toRgba()
        } finally {
            rgb.release()
            denoised.release()
            lab.release()
            contrastRgb.release()
            channels.forEach { channel -> channel.release() }
            sharpened?.release()
        }
    }

    private fun Mat.enhanceColor(): Mat {
        val rgb = toRgb()
        var balanced: Mat? = null
        val denoised = Mat()
        var sharpened: Mat? = null
        return try {
            balanced = rgb.whiteBalanced()
            Imgproc.bilateralFilter(balanced, denoised, COLOR_BILATERAL_DIAMETER, COLOR_SIGMA, COLOR_SIGMA)
            sharpened = denoised.sharpen(amount = COLOR_SHARPEN_AMOUNT)
            sharpened.toRgba()
        } finally {
            rgb.release()
            balanced?.release()
            denoised.release()
            sharpened?.release()
        }
    }

    private fun Mat.toGray(): Mat {
        val output = Mat()
        when (channels()) {
            GRAY_CHANNELS -> copyTo(output)
            RGB_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGB2GRAY)
            RGBA_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2GRAY)
            else -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2GRAY)
        }
        return output
    }

    private fun Mat.toRgb(): Mat {
        val output = Mat()
        when (channels()) {
            GRAY_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_GRAY2RGB)
            RGB_CHANNELS -> copyTo(output)
            RGBA_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2RGB)
            else -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2RGB)
        }
        return output
    }

    private fun Mat.toRgba(): Mat {
        val output = Mat()
        when (channels()) {
            GRAY_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_GRAY2RGBA)
            RGB_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGB2RGBA)
            RGBA_CHANNELS -> copyTo(output)
            else -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGB2RGBA)
        }
        return output
    }

    private fun Mat.sharpen(amount: Double): Mat {
        val blurred = Mat()
        val output = Mat()
        return try {
            Imgproc.GaussianBlur(this, blurred, Size(0.0, 0.0), SHARPEN_BLUR_SIGMA)
            Core.addWeighted(this, 1.0 + amount, blurred, -amount, 0.0, output)
            output
        } catch (throwable: Throwable) {
            output.release()
            throw throwable
        } finally {
            blurred.release()
        }
    }

    private fun Mat.whiteBalanced(): Mat {
        val channels = mutableListOf<Mat>()
        val output = Mat()
        return try {
            Core.split(this, channels)
            val means = channels.map { channel -> Core.mean(channel).`val`[0].coerceAtLeast(MIN_CHANNEL_MEAN) }
            val targetMean = means.average()

            channels.forEachIndexed { index, channel ->
                val scale = (targetMean / means[index]).coerceIn(MIN_WHITE_BALANCE_SCALE, MAX_WHITE_BALANCE_SCALE)
                Core.multiply(channel, Scalar(scale), channel)
            }
            Core.merge(channels, output)
            output
        } catch (throwable: Throwable) {
            output.release()
            throw throwable
        } finally {
            channels.forEach { channel -> channel.release() }
        }
    }

    private fun Bitmap.writeJpeg(outputPath: String) {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { outputStream ->
            if (!compress(Bitmap.CompressFormat.JPEG, ENHANCED_JPEG_QUALITY, outputStream)) {
                throw IllegalStateException("Enhanced image could not be written.")
            }
        }
    }

    private fun processingError(message: String): AppResult.Error = AppResult.Error(
        message = message,
        category = AppErrorCategory.PROCESSING
    )

    private companion object {
        const val ENHANCEMENT_MAX_LONG_EDGE_PX = 2400
        const val ENHANCED_JPEG_QUALITY = 92

        const val GRAY_CHANNELS = 1
        const val RGB_CHANNELS = 3
        const val RGBA_CHANNELS = 4

        const val MAX_BINARY_VALUE = 255.0
        const val DOCUMENT_MEDIAN_KERNEL_SIZE = 3
        const val DOCUMENT_THRESHOLD_BLOCK_SIZE = 25
        const val DOCUMENT_THRESHOLD_C = 12.0
        const val DOCUMENT_SHARPEN_AMOUNT = 0.35

        const val MIXED_BILATERAL_DIAMETER = 7
        const val MIXED_SIGMA = 45.0
        const val MIXED_CLAHE_CLIP_LIMIT = 1.6
        const val MIXED_SHARPEN_AMOUNT = 0.45

        const val COLOR_BILATERAL_DIAMETER = 5
        const val COLOR_SIGMA = 28.0
        const val COLOR_SHARPEN_AMOUNT = 0.2

        const val CLAHE_TILE_SIZE = 8.0
        const val SHARPEN_BLUR_SIGMA = 1.0
        const val MIN_CHANNEL_MEAN = 1.0
        const val MIN_WHITE_BALANCE_SCALE = 0.75
        const val MAX_WHITE_BALANCE_SCALE = 1.25
    }
}
