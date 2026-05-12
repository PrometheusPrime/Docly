package com.docly.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ScannedPage
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface ScanPageRenderer {
    suspend fun render(page: ScannedPage, outputPath: String): AppResult<String>
}

class AndroidScanPageRenderer @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    private val dispatcherProvider: DispatcherProvider
) : ScanPageRenderer {
    override suspend fun render(page: ScannedPage, outputPath: String): AppResult<String> {
        val inputPath = page.processedImagePath ?: page.originalImagePath
        if (inputPath.isBlank() || outputPath.isBlank()) {
            return AppResult.Error(
                message = "Page image is missing.",
                category = AppErrorCategory.STORAGE
            )
        }

        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = inputPath,
                maxWidth = RENDER_MAX_LONG_EDGE_PX,
                maxHeight = RENDER_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        var outputBitmap: Bitmap? = null
        var shouldRecycleDecodedBitmap = true
        return try {
            outputBitmap = withContext(dispatcherProvider.default) {
                decodedBitmap.toRenderedBitmap(page.rotationDegrees)
            }
            decodedBitmap.recycle()
            shouldRecycleDecodedBitmap = false
            withContext(dispatcherProvider.io) {
                outputBitmap?.writeJpeg(outputPath)
                    ?: throw IllegalStateException("Rendered page was not created.")
            }
            AppResult.Success(outputPath)
        } catch (throwable: Throwable) {
            File(outputPath).delete()
            AppResult.Error(
                message = "Page image could not be prepared.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            outputBitmap?.recycle()
            if (shouldRecycleDecodedBitmap) {
                decodedBitmap.recycle()
            }
        }
    }

    private fun Bitmap.toRenderedBitmap(rotationDegrees: Int): Bitmap {
        val rotatedBitmap = rotatedBy(rotationDegrees)
        val jpegBitmap = Bitmap.createBitmap(rotatedBitmap.width, rotatedBitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(jpegBitmap).apply {
            drawColor(Color.WHITE)
            drawBitmap(rotatedBitmap, 0f, 0f, null)
        }
        if (rotatedBitmap !== this) {
            rotatedBitmap.recycle()
        }
        return jpegBitmap
    }

    private fun Bitmap.rotatedBy(rotationDegrees: Int): Bitmap {
        val normalizedDegrees = ((rotationDegrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES) %
            FULL_ROTATION_DEGREES
        if (normalizedDegrees == 0) return this

        val matrix = Matrix().apply {
            postRotate(normalizedDegrees.toFloat())
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.writeJpeg(outputPath: String) {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { outputStream ->
            if (!compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                throw IllegalStateException("Rendered page could not be written.")
            }
        }
    }

    private companion object {
        const val RENDER_MAX_LONG_EDGE_PX = 4096
        const val JPEG_QUALITY = 92
        const val FULL_ROTATION_DEGREES = 360
    }
}
