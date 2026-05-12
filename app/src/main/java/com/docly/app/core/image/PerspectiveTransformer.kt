package com.docly.app.core.image

import android.graphics.Bitmap
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import java.io.File
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class WarpResult(val outputPath: String, val width: Int, val height: Int)

interface PerspectiveTransformer {
    suspend fun warp(imagePath: String, corners: PageCorners, outputPath: String): AppResult<WarpResult>
}

class OpenCvPerspectiveTransformer @Inject constructor(
    private val openCvInitializer: OpenCvInitializer,
    private val bitmapLoader: BitmapLoader,
    private val dispatcherProvider: DispatcherProvider
) : PerspectiveTransformer {
    override suspend fun warp(imagePath: String, corners: PageCorners, outputPath: String): AppResult<WarpResult> {
        if (imagePath.isBlank()) {
            return processingError("Image path is required for perspective correction.")
        }
        if (outputPath.isBlank()) {
            return processingError("Output path is required for perspective correction.")
        }

        when (val initializationResult = openCvInitializer.initialize()) {
            is AppResult.Error -> return initializationResult
            is AppResult.Success -> Unit
        }

        val originalDimensions = readOrientedImageDimensions(imagePath)
            ?: return processingError("Image could not be read for perspective correction.")

        val decodedBitmap = when (
            val decodeResult = bitmapLoader.decode(
                path = imagePath,
                maxWidth = PERSPECTIVE_MAX_LONG_EDGE_PX,
                maxHeight = PERSPECTIVE_MAX_LONG_EDGE_PX
            )
        ) {
            is AppResult.Error -> return decodeResult
            is AppResult.Success -> decodeResult.data
        }

        var warpedBitmap: Bitmap? = null
        var shouldRecycleDecodedBitmap = true
        return try {
            val preparedWarp = withContext(dispatcherProvider.default) {
                val orderedCorners = CornerOrderingUtil.orderCorners(
                    corners
                        .scaledToBitmap(
                            bitmapWidth = decodedBitmap.width,
                            bitmapHeight = decodedBitmap.height,
                            originalDimensions = originalDimensions
                        )
                        .asList()
                ) ?: throw IllegalArgumentException("Document corners are invalid.")
                val targetDimensions = orderedCorners.targetDimensions()
                    ?: throw IllegalArgumentException("Perspective output dimensions are invalid.")
                val bitmap = decodedBitmap.warpToBitmap(
                    corners = orderedCorners,
                    targetDimensions = targetDimensions
                )

                warpedBitmap = bitmap
                PreparedWarp(
                    bitmap = bitmap,
                    width = targetDimensions.width,
                    height = targetDimensions.height
                )
            }
            decodedBitmap.recycle()
            shouldRecycleDecodedBitmap = false

            withContext(dispatcherProvider.io) {
                preparedWarp.bitmap.writeJpeg(outputPath)
            }

            AppResult.Success(
                WarpResult(
                    outputPath = outputPath,
                    width = preparedWarp.width,
                    height = preparedWarp.height
                )
            )
        } catch (throwable: Throwable) {
            File(outputPath).delete()
            AppResult.Error(
                message = "Perspective correction failed.",
                category = AppErrorCategory.PROCESSING,
                throwable = throwable
            )
        } finally {
            warpedBitmap?.recycle()
            if (shouldRecycleDecodedBitmap) {
                decodedBitmap.recycle()
            }
        }
    }

    private fun PageCorners.scaledToBitmap(
        bitmapWidth: Int,
        bitmapHeight: Int,
        originalDimensions: ImageDimensions
    ): PageCorners = scaledBy(
        scaleX = bitmapWidth.toFloat() / originalDimensions.width.toFloat(),
        scaleY = bitmapHeight.toFloat() / originalDimensions.height.toFloat()
    )

    private fun PageCorners.targetDimensions(): ImageDimensions? {
        val width = maxOf(
            topLeft.distanceTo(topRight),
            bottomLeft.distanceTo(bottomRight)
        ).roundToInt()
        val height = maxOf(
            topRight.distanceTo(bottomRight),
            topLeft.distanceTo(bottomLeft)
        ).roundToInt()

        return if (width >= MIN_OUTPUT_EDGE_PX && height >= MIN_OUTPUT_EDGE_PX) {
            ImageDimensions(width = width, height = height)
        } else {
            null
        }
    }

    private fun Bitmap.warpToBitmap(corners: PageCorners, targetDimensions: ImageDimensions): Bitmap {
        val sourceMat = Mat()
        val warpedMat = Mat()
        val sourcePoints = MatOfPoint2f(*corners.toOpenCvPoints())
        val targetPoints = MatOfPoint2f(*targetDimensions.toOpenCvPoints())
        var transformMat: Mat? = null
        var outputBitmap: Bitmap? = null

        try {
            Utils.bitmapToMat(this, sourceMat)
            val perspectiveTransform = Imgproc.getPerspectiveTransform(sourcePoints, targetPoints)
            transformMat = perspectiveTransform
            Imgproc.warpPerspective(
                sourceMat,
                warpedMat,
                perspectiveTransform,
                Size(targetDimensions.width.toDouble(), targetDimensions.height.toDouble())
            )

            outputBitmap = Bitmap.createBitmap(
                targetDimensions.width,
                targetDimensions.height,
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(warpedMat, outputBitmap)
            return outputBitmap
        } catch (throwable: Throwable) {
            outputBitmap?.recycle()
            throw throwable
        } finally {
            sourceMat.release()
            warpedMat.release()
            transformMat?.release()
            sourcePoints.release()
            targetPoints.release()
        }
    }

    private fun Bitmap.writeJpeg(outputPath: String) {
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { outputStream ->
            if (!compress(Bitmap.CompressFormat.JPEG, PROCESSED_JPEG_QUALITY, outputStream)) {
                throw IllegalStateException("Perspective output could not be written.")
            }
        }
    }

    private fun PageCorners.asList(): List<PointFSerializable> = listOf(
        topLeft,
        topRight,
        bottomRight,
        bottomLeft
    )

    private fun PageCorners.toOpenCvPoints(): Array<Point> = arrayOf(
        topLeft.toOpenCvPoint(),
        topRight.toOpenCvPoint(),
        bottomRight.toOpenCvPoint(),
        bottomLeft.toOpenCvPoint()
    )

    private fun ImageDimensions.toOpenCvPoints(): Array<Point> = arrayOf(
        Point(0.0, 0.0),
        Point((width - 1).toDouble(), 0.0),
        Point((width - 1).toDouble(), (height - 1).toDouble()),
        Point(0.0, (height - 1).toDouble())
    )

    private fun PointFSerializable.toOpenCvPoint(): Point = Point(x.toDouble(), y.toDouble())

    private fun PointFSerializable.distanceTo(other: PointFSerializable): Float = hypot(
        (x - other.x).toDouble(),
        (y - other.y).toDouble()
    ).toFloat()

    private fun processingError(message: String): AppResult.Error = AppResult.Error(
        message = message,
        category = AppErrorCategory.PROCESSING
    )

    private data class PreparedWarp(val bitmap: Bitmap, val width: Int, val height: Int)

    private companion object {
        const val PERSPECTIVE_MAX_LONG_EDGE_PX = 2400
        const val MIN_OUTPUT_EDGE_PX = 2
        const val PROCESSED_JPEG_QUALITY = 92
    }
}
