package com.docly.app.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

data class ImageDimensions(val width: Int, val height: Int)

interface BitmapLoader {
    suspend fun decode(path: String, maxWidth: Int, maxHeight: Int): AppResult<Bitmap>
}

class AndroidBitmapLoader @Inject constructor(private val dispatcherProvider: DispatcherProvider) : BitmapLoader {
    override suspend fun decode(path: String, maxWidth: Int, maxHeight: Int): AppResult<Bitmap> =
        withContext(dispatcherProvider.default) {
            try {
                if (maxWidth <= 0 || maxHeight <= 0) {
                    return@withContext AppResult.Error(
                        message = "Invalid bitmap decode bounds.",
                        category = AppErrorCategory.PROCESSING
                    )
                }

                val sourceDimensions = readRawImageDimensions(path)
                    ?: return@withContext AppResult.Error(
                        message = "Image could not be read.",
                        category = AppErrorCategory.PROCESSING
                    )
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = calculateBitmapSampleSize(
                        sourceWidth = sourceDimensions.width,
                        sourceHeight = sourceDimensions.height,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight
                    )
                }
                val decoded = BitmapFactory.decodeFile(path, options)
                    ?: return@withContext AppResult.Error(
                        message = "Image could not be decoded.",
                        category = AppErrorCategory.PROCESSING
                    )

                AppResult.Success(applyExifTransform(path = path, bitmap = decoded))
            } catch (throwable: Throwable) {
                AppResult.Error(
                    message = "Image could not be decoded.",
                    category = AppErrorCategory.PROCESSING,
                    throwable = throwable
                )
            }
        }
}

fun calculateBitmapSampleSize(sourceWidth: Int, sourceHeight: Int, maxWidth: Int, maxHeight: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) {
        return 1
    }

    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * DOUBLE_SAMPLE_SIZE) >= maxWidth ||
        sourceHeight / (sampleSize * DOUBLE_SAMPLE_SIZE) >= maxHeight
    ) {
        sampleSize *= DOUBLE_SAMPLE_SIZE
    }
    return sampleSize
}

fun readRawImageDimensions(path: String): ImageDimensions? {
    if (path.isBlank() || !File(path).isFile) return null

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(path, options)

    return if (options.outWidth > 0 && options.outHeight > 0) {
        ImageDimensions(width = options.outWidth, height = options.outHeight)
    } else {
        null
    }
}

fun readOrientedImageDimensions(path: String): ImageDimensions? {
    val rawDimensions = readRawImageDimensions(path) ?: return null
    val orientation = readExifOrientation(path)
    return dimensionsForExifOrientation(
        width = rawDimensions.width,
        height = rawDimensions.height,
        orientation = orientation
    )
}

fun dimensionsForExifOrientation(width: Int, height: Int, orientation: Int): ImageDimensions = when (orientation) {
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270 -> ImageDimensions(width = height, height = width)

    else -> ImageDimensions(width = width, height = height)
}

private fun applyExifTransform(path: String, bitmap: Bitmap): Bitmap {
    val matrix = matrixForExifOrientation(readExifOrientation(path)) ?: return bitmap
    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (transformed != bitmap) {
        bitmap.recycle()
    }
    return transformed
}

private fun matrixForExifOrientation(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)

        else -> return null
    }
    return matrix
}

private fun readExifOrientation(path: String): Int = runCatching {
    ExifInterface(path).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private const val DOUBLE_SAMPLE_SIZE = 2
