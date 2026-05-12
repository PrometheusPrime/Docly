package com.docly.app.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

interface ThumbnailGenerator {
    suspend fun generate(inputPath: String, outputPath: String): AppResult<String>
}

class AndroidThumbnailGenerator @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    private val dispatcherProvider: DispatcherProvider
) : ThumbnailGenerator {
    override suspend fun generate(inputPath: String, outputPath: String): AppResult<String> {
        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = inputPath,
                maxWidth = THUMBNAIL_MAX_LONG_EDGE_PX,
                maxHeight = THUMBNAIL_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        var thumbnailBitmap: Bitmap? = null
        var shouldRecycleDecodedBitmap = true
        return try {
            thumbnailBitmap = withContext(dispatcherProvider.default) {
                decodedBitmap.toThumbnailBitmap()
            }
            decodedBitmap.recycle()
            shouldRecycleDecodedBitmap = false
            withContext(dispatcherProvider.io) {
                val outputFile = File(outputPath)
                outputFile.parentFile?.mkdirs()
                val bitmapToWrite = thumbnailBitmap ?: throw IllegalStateException("Thumbnail was not created.")
                outputFile.outputStream().use { outputStream ->
                    if (!bitmapToWrite.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, outputStream)) {
                        throw IllegalStateException("Thumbnail could not be written.")
                    }
                }
            }
            AppResult.Success(outputPath)
        } catch (throwable: Throwable) {
            File(outputPath).delete()
            AppResult.Error(
                message = "Thumbnail could not be generated.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            thumbnailBitmap?.recycle()
            if (shouldRecycleDecodedBitmap) {
                decodedBitmap.recycle()
            }
        }
    }

    private fun Bitmap.toThumbnailBitmap(): Bitmap {
        val scale = minOf(
            THUMBNAIL_MAX_LONG_EDGE_PX / width.toFloat(),
            THUMBNAIL_MAX_LONG_EDGE_PX / height.toFloat(),
            1f
        )
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = if (targetWidth == width && targetHeight == height) {
            this
        } else {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        }
        val jpegBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(jpegBitmap).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaledBitmap, 0f, 0f, null)
        }
        if (scaledBitmap != this) {
            scaledBitmap.recycle()
        }
        return jpegBitmap
    }

    private companion object {
        const val THUMBNAIL_MAX_LONG_EDGE_PX = 512
        const val THUMBNAIL_JPEG_QUALITY = 82
    }
}
