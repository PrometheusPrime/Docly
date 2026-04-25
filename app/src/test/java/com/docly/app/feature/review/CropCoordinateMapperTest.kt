package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropCoordinateMapperTest {
    @Test
    fun fitScalingUsesVerticalLetterboxWhenViewportIsWiderThanImage() {
        val mapper = CropCoordinateMapper(
            imageWidth = 100f,
            imageHeight = 200f,
            viewportWidth = 300f,
            viewportHeight = 300f
        )

        assertEquals(1.5f, mapper.scale, FLOAT_TOLERANCE)
        assertEquals(75f, mapper.horizontalOffset, FLOAT_TOLERANCE)
        assertEquals(0f, mapper.verticalOffset, FLOAT_TOLERANCE)
        assertEquals(CropPoint(150f, 150f), mapper.imageToViewport(PointFSerializable(50f, 100f)))
    }

    @Test
    fun fitScalingUsesHorizontalLetterboxWhenViewportIsTallerThanImage() {
        val mapper = CropCoordinateMapper(
            imageWidth = 200f,
            imageHeight = 100f,
            viewportWidth = 300f,
            viewportHeight = 300f
        )

        assertEquals(1.5f, mapper.scale, FLOAT_TOLERANCE)
        assertEquals(0f, mapper.horizontalOffset, FLOAT_TOLERANCE)
        assertEquals(75f, mapper.verticalOffset, FLOAT_TOLERANCE)
        assertEquals(CropPoint(150f, 150f), mapper.imageToViewport(PointFSerializable(100f, 50f)))
    }

    @Test
    fun viewportCoordinatesMapBackToImageCoordinates() {
        val mapper = CropCoordinateMapper(
            imageWidth = 100f,
            imageHeight = 200f,
            viewportWidth = 300f,
            viewportHeight = 300f
        )

        assertEquals(PointFSerializable(50f, 100f), mapper.viewportToImage(CropPoint(150f, 150f)))
    }

    @Test
    fun viewportDragDeltaMapsToImageDelta() {
        val mapper = CropCoordinateMapper(
            imageWidth = 100f,
            imageHeight = 200f,
            viewportWidth = 300f,
            viewportHeight = 300f
        )

        assertEquals(CropDelta(10f, -5f), mapper.viewportDeltaToImageDelta(deltaX = 15f, deltaY = -7.5f))
    }

    @Test
    fun movedCornerIsClampedToImageBounds() {
        val mapper = CropCoordinateMapper(
            imageWidth = 100f,
            imageHeight = 200f,
            viewportWidth = 100f,
            viewportHeight = 200f
        )

        val movedCorners = mapper.moveCornerByViewportDelta(
            corners = sampleCorners(),
            corner = CropCorner.TOP_LEFT,
            deltaX = -40f,
            deltaY = 300f
        )

        assertEquals(PointFSerializable(0f, 199f), movedCorners.topLeft)
    }

    @Test
    fun factoryRejectsInvalidDimensions() {
        assertNull(
            CropCoordinateMapper.create(
                imageWidth = 0,
                imageHeight = 100,
                viewportWidth = 200,
                viewportHeight = 200
            )
        )
        assertNull(
            CropCoordinateMapper.create(
                imageWidth = 100,
                imageHeight = 100,
                viewportWidth = 0,
                viewportHeight = 200
            )
        )
    }

    @Test
    fun fullImageCornersUseMaximumValidPixelCoordinates() {
        assertEquals(
            PageCorners(
                topLeft = PointFSerializable(0f, 0f),
                topRight = PointFSerializable(99f, 0f),
                bottomRight = PointFSerializable(99f, 199f),
                bottomLeft = PointFSerializable(0f, 199f)
            ),
            fullImageCorners(imageWidth = 100, imageHeight = 200)
        )
    }

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(10f, 20f),
        topRight = PointFSerializable(90f, 25f),
        bottomRight = PointFSerializable(80f, 180f),
        bottomLeft = PointFSerializable(15f, 170f)
    )

    private companion object {
        const val FLOAT_TOLERANCE = 0.001f
    }
}
