package com.docly.app.core.image

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScanQualityScorerTest {
    private val scorer = DefaultScanQualityScorer()

    @Test
    fun goodScanHasNoQualityIssues() {
        val assessment = scorer.score(goodImage(), documentCorners())

        assertFalse(assessment.hasWarnings)
    }

    @Test
    fun lowLaplacianVarianceFlagsBlurryScan() {
        val assessment = scorer.score(uniformImage(luminance = 128), documentCorners())

        assertTrue(ScanQualityIssue.BLURRY in assessment.issues)
    }

    @Test
    fun lowMeanLuminanceFlagsDarkScan() {
        val assessment = scorer.score(uniformImage(luminance = 50), documentCorners())

        assertTrue(ScanQualityIssue.TOO_DARK in assessment.issues)
    }

    @Test
    fun highMeanLuminanceFlagsBrightScan() {
        val assessment = scorer.score(uniformImage(luminance = 230), documentCorners())

        assertTrue(ScanQualityIssue.TOO_BRIGHT in assessment.issues)
    }

    @Test
    fun overexposedPixelRatioFlagsOverexposedScan() {
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE) { index ->
            if (index < (IMAGE_SIZE * IMAGE_SIZE * 0.13).toInt()) 250 else 120
        }
        val assessment = scorer.score(LuminanceImage(IMAGE_SIZE, IMAGE_SIZE, pixels), documentCorners())

        assertTrue(ScanQualityIssue.OVEREXPOSED in assessment.issues)
    }

    @Test
    fun missingCornersFlagsDocumentNotDetected() {
        val assessment = scorer.score(goodImage(), corners = null)

        assertTrue(ScanQualityIssue.DOCUMENT_NOT_DETECTED in assessment.issues)
    }

    @Test
    fun smallDocumentAreaFlagsMoveCloserIssue() {
        val assessment = scorer.score(goodImage(), smallDocumentCorners())

        assertTrue(ScanQualityIssue.DOCUMENT_TOO_SMALL in assessment.issues)
    }

    private fun goodImage(): LuminanceImage {
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE) { index ->
            val x = index % IMAGE_SIZE
            val y = index / IMAGE_SIZE
            if ((x + y) % 2 == 0) 70 else 190
        }
        return LuminanceImage(width = IMAGE_SIZE, height = IMAGE_SIZE, pixels = pixels)
    }

    private fun uniformImage(luminance: Int): LuminanceImage {
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE) { luminance }
        return LuminanceImage(width = IMAGE_SIZE, height = IMAGE_SIZE, pixels = pixels)
    }

    private fun documentCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(5f, 5f),
        topRight = PointFSerializable(58f, 5f),
        bottomRight = PointFSerializable(58f, 58f),
        bottomLeft = PointFSerializable(5f, 58f)
    )

    private fun smallDocumentCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(20f, 20f),
        topRight = PointFSerializable(40f, 20f),
        bottomRight = PointFSerializable(40f, 40f),
        bottomLeft = PointFSerializable(20f, 40f)
    )

    private companion object {
        const val IMAGE_SIZE = 64
    }
}
