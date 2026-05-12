package com.docly.app.core.image

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapLoaderTest {
    @Test
    fun sampleSizeReturnsOneWhenSourceAlreadyFitsRequestedBounds() {
        val sampleSize = calculateBitmapSampleSize(
            sourceWidth = 800,
            sourceHeight = 600,
            maxWidth = 1024,
            maxHeight = 1024
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun sampleSizeDownsamplesLargeLandscapeImageByPowerOfTwo() {
        val sampleSize = calculateBitmapSampleSize(
            sourceWidth = 4000,
            sourceHeight = 1000,
            maxWidth = 512,
            maxHeight = 512
        )

        assertEquals(4, sampleSize)
    }

    @Test
    fun sampleSizeDownsamplesLargePortraitImageByPowerOfTwo() {
        val sampleSize = calculateBitmapSampleSize(
            sourceWidth = 3000,
            sourceHeight = 4000,
            maxWidth = 1024,
            maxHeight = 1024
        )

        assertEquals(2, sampleSize)
    }

    @Test
    fun sampleSizeReturnsSafeDefaultForInvalidDimensions() {
        val sampleSize = calculateBitmapSampleSize(
            sourceWidth = 0,
            sourceHeight = 4000,
            maxWidth = 1024,
            maxHeight = 1024
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun sampleSizeReturnsSafeDefaultForInvalidBounds() {
        val sampleSize = calculateBitmapSampleSize(
            sourceWidth = 4000,
            sourceHeight = 3000,
            maxWidth = 0,
            maxHeight = 1024
        )

        assertEquals(1, sampleSize)
    }

    @Test
    fun fittedDimensionsLeaveSmallerImageUnchanged() {
        val fittedDimensions = calculateFittedImageDimensions(
            sourceWidth = 800,
            sourceHeight = 600,
            maxWidth = 1200,
            maxHeight = 1200
        )

        assertEquals(ImageDimensions(width = 800, height = 600), fittedDimensions)
    }

    @Test
    fun fittedDimensionsCapLandscapeImageWithinBothBounds() {
        val fittedDimensions = calculateFittedImageDimensions(
            sourceWidth = 4000,
            sourceHeight = 2000,
            maxWidth = 1000,
            maxHeight = 800
        )

        assertEquals(ImageDimensions(width = 1000, height = 500), fittedDimensions)
    }

    @Test
    fun fittedDimensionsCapPortraitImageWithinBothBounds() {
        val fittedDimensions = calculateFittedImageDimensions(
            sourceWidth = 1200,
            sourceHeight = 2400,
            maxWidth = 900,
            maxHeight = 1000
        )

        assertEquals(ImageDimensions(width = 500, height = 1000), fittedDimensions)
    }

    @Test
    fun fittedDimensionsReturnSafeMinimumForInvalidInputs() {
        val fittedDimensions = calculateFittedImageDimensions(
            sourceWidth = 0,
            sourceHeight = -200,
            maxWidth = 0,
            maxHeight = 0
        )

        assertEquals(ImageDimensions(width = 1, height = 1), fittedDimensions)
    }

    @Test
    fun exifDimensionMappingSwapsDimensionsForQuarterTurns() {
        assertEquals(
            ImageDimensions(width = 600, height = 800),
            dimensionsForExifOrientation(
                width = 800,
                height = 600,
                orientation = ExifInterface.ORIENTATION_ROTATE_90
            )
        )
        assertEquals(
            ImageDimensions(width = 600, height = 800),
            dimensionsForExifOrientation(
                width = 800,
                height = 600,
                orientation = ExifInterface.ORIENTATION_ROTATE_270
            )
        )
    }

    @Test
    fun exifDimensionMappingKeepsDimensionsForNormalOrientation() {
        assertEquals(
            ImageDimensions(width = 800, height = 600),
            dimensionsForExifOrientation(
                width = 800,
                height = 600,
                orientation = ExifInterface.ORIENTATION_NORMAL
            )
        )
    }
}
