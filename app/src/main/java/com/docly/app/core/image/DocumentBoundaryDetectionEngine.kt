package com.docly.app.core.image

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import javax.inject.Inject
import kotlin.math.abs
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class DocumentBoundaryDetectionEngine @Inject constructor() {
    fun detect(source: Mat, maxLongEdgePx: Int): PageCorners? {
        if (source.empty() || source.width() <= 0 || source.height() <= 0 || maxLongEdgePx <= 0) {
            return null
        }

        val resizeScale = resizeScaleFor(source = source, maxLongEdgePx = maxLongEdgePx)
        val working = Mat()
        val ownsWorking = resizeScale < 1f
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(DILATION_KERNEL_SIZE.toDouble(), DILATION_KERNEL_SIZE.toDouble())
        )
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        try {
            val input = if (ownsWorking) {
                Imgproc.resize(
                    source,
                    working,
                    Size(),
                    resizeScale.toDouble(),
                    resizeScale.toDouble(),
                    Imgproc.INTER_AREA
                )
                working
            } else {
                source
            }

            input.toGray(gray)
            Imgproc.GaussianBlur(gray, blurred, Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE), 0.0)
            Imgproc.Canny(blurred, edges, CANNY_LOW_THRESHOLD, CANNY_HIGH_THRESHOLD)
            Imgproc.dilate(edges, dilated, kernel)
            Imgproc.findContours(
                dilated,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            return findBestQuadrilateral(
                contours = contours,
                imageArea = input.width().toDouble() * input.height().toDouble(),
                scaleToSource = 1f / resizeScale
            )
        } finally {
            if (ownsWorking) working.release()
            gray.release()
            blurred.release()
            edges.release()
            dilated.release()
            kernel.release()
            hierarchy.release()
            contours.forEach { contour -> contour.release() }
        }
    }

    private fun Mat.toGray(output: Mat) {
        when (channels()) {
            RGBA_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2GRAY)
            RGB_CHANNELS -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGB2GRAY)
            GRAY_CHANNELS -> copyTo(output)
            else -> Imgproc.cvtColor(this, output, Imgproc.COLOR_RGBA2GRAY)
        }
    }

    private fun findBestQuadrilateral(
        contours: List<MatOfPoint>,
        imageArea: Double,
        scaleToSource: Float
    ): PageCorners? {
        val minContourArea = imageArea * MIN_DOCUMENT_AREA_RATIO
        var bestArea = 0.0
        var bestCorners: PageCorners? = null

        contours.forEach { contour ->
            val contourArea = abs(Imgproc.contourArea(contour))
            if (contourArea < minContourArea || contourArea <= bestArea) return@forEach

            val contour2f = MatOfPoint2f(*contour.toArray())
            val approximation = MatOfPoint2f()
            try {
                val perimeter = Imgproc.arcLength(contour2f, true)
                Imgproc.approxPolyDP(contour2f, approximation, APPROXIMATION_EPSILON_RATIO * perimeter, true)
                val points = approximation.toArray()
                if (points.size != EXPECTED_CORNER_COUNT) return@forEach
                val approximatedContour = MatOfPoint(*points)
                try {
                    if (!Imgproc.isContourConvex(approximatedContour)) return@forEach
                } finally {
                    approximatedContour.release()
                }

                val orderedCorners = CornerOrderingUtil.orderCorners(
                    points.map { point ->
                        PointFSerializable(
                            x = (point.x * scaleToSource).toFloat(),
                            y = (point.y * scaleToSource).toFloat()
                        )
                    }
                ) ?: return@forEach

                bestArea = contourArea
                bestCorners = orderedCorners
            } finally {
                contour2f.release()
                approximation.release()
            }
        }

        return bestCorners
    }

    private fun resizeScaleFor(source: Mat, maxLongEdgePx: Int): Float {
        val longEdge = maxOf(source.width(), source.height())
        return if (longEdge > maxLongEdgePx) {
            maxLongEdgePx.toFloat() / longEdge.toFloat()
        } else {
            1f
        }
    }

    private companion object {
        const val GRAY_CHANNELS = 1
        const val RGB_CHANNELS = 3
        const val RGBA_CHANNELS = 4
        const val EXPECTED_CORNER_COUNT = 4
        const val MIN_DOCUMENT_AREA_RATIO = 0.08
        const val APPROXIMATION_EPSILON_RATIO = 0.02
        const val BLUR_KERNEL_SIZE = 5.0
        const val CANNY_LOW_THRESHOLD = 75.0
        const val CANNY_HIGH_THRESHOLD = 200.0
        const val DILATION_KERNEL_SIZE = 3
    }
}
