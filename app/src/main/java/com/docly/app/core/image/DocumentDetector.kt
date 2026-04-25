package com.docly.app.core.image

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat

interface DocumentDetector {
    suspend fun detect(imagePath: String): AppResult<PageCorners?>
}

class OpenCvDocumentDetector @Inject constructor(
    private val openCvInitializer: OpenCvInitializer,
    private val bitmapLoader: BitmapLoader,
    private val detectionEngine: DocumentBoundaryDetectionEngine,
    private val dispatcherProvider: DispatcherProvider
) : DocumentDetector {
    override suspend fun detect(imagePath: String): AppResult<PageCorners?> {
        when (val initializationResult = openCvInitializer.initialize()) {
            is AppResult.Error -> return initializationResult
            is AppResult.Success -> Unit
        }

        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = imagePath,
                maxWidth = DETECTION_MAX_LONG_EDGE_PX,
                maxHeight = DETECTION_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        val sourceMat = Mat()
        return try {
            withContext(dispatcherProvider.default) {
                Utils.bitmapToMat(decodedBitmap, sourceMat)
                val detectedCorners = detectionEngine.detect(
                    source = sourceMat,
                    maxLongEdgePx = DETECTION_MAX_LONG_EDGE_PX
                )
                val originalDimensions = readOrientedImageDimensions(imagePath)
                val scaledCorners = if (detectedCorners != null && originalDimensions != null) {
                    detectedCorners.scaledBy(
                        scaleX = originalDimensions.width.toFloat() / decodedBitmap.width.toFloat(),
                        scaleY = originalDimensions.height.toFloat() / decodedBitmap.height.toFloat()
                    )
                } else {
                    detectedCorners
                }
                AppResult.Success(scaledCorners)
            }
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Document boundary detection failed.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            sourceMat.release()
            decodedBitmap.recycle()
        }
    }

    private companion object {
        const val DETECTION_MAX_LONG_EDGE_PX = 1600
    }
}
