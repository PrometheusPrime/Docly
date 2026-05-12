package com.docly.app.core.image

import android.graphics.Bitmap
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface ScanQualityEvaluator {
    suspend fun evaluate(imagePath: String, corners: PageCorners?): AppResult<ScanQualityAssessment>
}

class AndroidScanQualityEvaluator @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    private val scanQualityScorer: ScanQualityScorer,
    private val dispatcherProvider: DispatcherProvider
) : ScanQualityEvaluator {
    override suspend fun evaluate(imagePath: String, corners: PageCorners?): AppResult<ScanQualityAssessment> {
        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = imagePath,
                maxWidth = QUALITY_EVALUATION_MAX_LONG_EDGE_PX,
                maxHeight = QUALITY_EVALUATION_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        return try {
            withContext(dispatcherProvider.default) {
                AppResult.Success(scanQualityScorer.score(decodedBitmap.toLuminanceImage(), corners))
            }
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Scan quality could not be evaluated.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            decodedBitmap.recycle()
        }
    }

    private fun Bitmap.toLuminanceImage(): LuminanceImage {
        val sourcePixels = IntArray(width * height)
        val luminancePixels = IntArray(width * height)
        getPixels(sourcePixels, 0, width, 0, 0, width, height)

        sourcePixels.forEachIndexed { index, argb ->
            val red = (argb shr RED_SHIFT) and BYTE_MASK
            val green = (argb shr GREEN_SHIFT) and BYTE_MASK
            val blue = argb and BYTE_MASK
            luminancePixels[index] = rgbToLuminance(red = red, green = green, blue = blue)
        }

        return LuminanceImage(width = width, height = height, pixels = luminancePixels)
    }

    private companion object {
        const val QUALITY_EVALUATION_MAX_LONG_EDGE_PX = 1200
        const val RED_SHIFT = 16
        const val GREEN_SHIFT = 8
        const val BYTE_MASK = 0xFF
    }
}

fun rgbToLuminance(red: Int, green: Int, blue: Int): Int {
    val weighted = LUMA_RED_WEIGHT * red.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE) +
        LUMA_GREEN_WEIGHT * green.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE) +
        LUMA_BLUE_WEIGHT * blue.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)
    return weighted.toInt().coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)
}

private const val LUMA_RED_WEIGHT = 0.299
private const val LUMA_GREEN_WEIGHT = 0.587
private const val LUMA_BLUE_WEIGHT = 0.114
