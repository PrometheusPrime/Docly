package com.docly.app.core.image

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow

data class LuminanceImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0) { "Image width must be positive." }
        require(height > 0) { "Image height must be positive." }
        require(pixels.size == width * height) { "Luminance buffer must match image dimensions." }
    }

    fun luminanceAt(x: Int, y: Int): Int = pixels[y * width + x].coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LuminanceImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

data class ScanQualityAssessment(
    val blurScore: Double,
    val brightnessScore: Double,
    val overexposureScore: Double,
    val documentAreaScore: Double,
    val issues: Set<ScanQualityIssue>
) {
    val hasWarnings: Boolean
        get() = issues.isNotEmpty()

    companion object {
        fun good(
            blurScore: Double = GOOD_BLUR_SCORE,
            brightnessScore: Double = GOOD_BRIGHTNESS_SCORE,
            overexposureScore: Double = 0.0,
            documentAreaScore: Double = GOOD_DOCUMENT_AREA_SCORE
        ): ScanQualityAssessment = ScanQualityAssessment(
            blurScore = blurScore,
            brightnessScore = brightnessScore,
            overexposureScore = overexposureScore,
            documentAreaScore = documentAreaScore,
            issues = emptySet()
        )
    }
}

enum class ScanQualityIssue {
    BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    OVEREXPOSED,
    DOCUMENT_TOO_SMALL,
    DOCUMENT_NOT_DETECTED
}

interface ScanQualityScorer {
    fun score(luminanceImage: LuminanceImage, corners: PageCorners?): ScanQualityAssessment
}

class DefaultScanQualityScorer @Inject constructor() : ScanQualityScorer {
    override fun score(luminanceImage: LuminanceImage, corners: PageCorners?): ScanQualityAssessment {
        val brightnessScore = luminanceImage.meanLuminance()
        val blurScore = luminanceImage.laplacianVariance()
        val overexposureScore = luminanceImage.overexposedPixelRatio()
        val documentAreaScore = corners?.areaRatioFor(luminanceImage) ?: 0.0

        val issues = buildSet {
            if (blurScore < BLUR_VARIANCE_THRESHOLD) add(ScanQualityIssue.BLURRY)
            if (brightnessScore < DARK_MEAN_LUMINANCE_THRESHOLD) add(ScanQualityIssue.TOO_DARK)
            if (brightnessScore > BRIGHT_MEAN_LUMINANCE_THRESHOLD) add(ScanQualityIssue.TOO_BRIGHT)
            if (overexposureScore > OVEREXPOSED_PIXEL_RATIO_THRESHOLD) add(ScanQualityIssue.OVEREXPOSED)
            if (corners == null) {
                add(ScanQualityIssue.DOCUMENT_NOT_DETECTED)
            } else if (documentAreaScore < DOCUMENT_AREA_RATIO_THRESHOLD) {
                add(ScanQualityIssue.DOCUMENT_TOO_SMALL)
            }
        }

        return ScanQualityAssessment(
            blurScore = blurScore,
            brightnessScore = brightnessScore,
            overexposureScore = overexposureScore,
            documentAreaScore = documentAreaScore,
            issues = issues
        )
    }

    private fun LuminanceImage.meanLuminance(): Double = pixels
        .fold(0L) { total, value -> total + value.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE) }
        .toDouble() / pixels.size.toDouble()

    private fun LuminanceImage.overexposedPixelRatio(): Double {
        val overexposedPixels = pixels.count { value ->
            value.coerceIn(MIN_LUMINANCE, MAX_LUMINANCE) >= OVEREXPOSED_LUMINANCE
        }
        return overexposedPixels.toDouble() / pixels.size.toDouble()
    }

    private fun LuminanceImage.laplacianVariance(): Double {
        if (width < MIN_LAPLACIAN_EDGE || height < MIN_LAPLACIAN_EDGE) return 0.0

        val values = ArrayList<Double>((width - 2) * (height - 2))
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = luminanceAt(x, y) * LAPLACIAN_CENTER_WEIGHT
                val neighbors = luminanceAt(x - 1, y) +
                    luminanceAt(x + 1, y) +
                    luminanceAt(x, y - 1) +
                    luminanceAt(x, y + 1)
                values += (center - neighbors).toDouble()
            }
        }
        if (values.isEmpty()) return 0.0

        val mean = values.sum() / values.size.toDouble()
        return values.sumOf { value -> (value - mean).pow(2.0) } / values.size.toDouble()
    }

    private fun PageCorners.areaRatioFor(luminanceImage: LuminanceImage): Double {
        val polygonArea = abs(
            topLeft.cross(topRight) +
                topRight.cross(bottomRight) +
                bottomRight.cross(bottomLeft) +
                bottomLeft.cross(topLeft)
        ) / 2.0
        val imageArea = luminanceImage.width.toDouble() * luminanceImage.height.toDouble()
        return (polygonArea / imageArea).coerceIn(0.0, 1.0)
    }

    private fun PointFSerializable.cross(other: PointFSerializable): Double =
        x.toDouble() * other.y.toDouble() - y.toDouble() * other.x.toDouble()

    private companion object {
        const val BLUR_VARIANCE_THRESHOLD = 80.0
        const val DARK_MEAN_LUMINANCE_THRESHOLD = 70.0
        const val BRIGHT_MEAN_LUMINANCE_THRESHOLD = 215.0
        const val OVEREXPOSED_LUMINANCE = 245
        const val OVEREXPOSED_PIXEL_RATIO_THRESHOLD = 0.12
        const val DOCUMENT_AREA_RATIO_THRESHOLD = 0.25
        const val LAPLACIAN_CENTER_WEIGHT = 4
        const val MIN_LAPLACIAN_EDGE = 3
    }
}

const val MIN_LUMINANCE = 0
const val MAX_LUMINANCE = 255

private const val GOOD_BLUR_SCORE = 160.0
private const val GOOD_BRIGHTNESS_SCORE = 128.0
private const val GOOD_DOCUMENT_AREA_SCORE = 0.75
