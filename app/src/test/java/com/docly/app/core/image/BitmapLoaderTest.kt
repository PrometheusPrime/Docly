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
