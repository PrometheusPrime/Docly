package com.docly.app.core.image

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CornerOrderingUtilTest {
    @Test
    fun orderCornersReturnsExpectedClockwisePageCorners() {
        val topLeft = PointFSerializable(20f, 30f)
        val topRight = PointFSerializable(180f, 40f)
        val bottomRight = PointFSerializable(170f, 260f)
        val bottomLeft = PointFSerializable(25f, 240f)

        val orderedCorners = CornerOrderingUtil.orderCorners(
            listOf(bottomRight, topRight, bottomLeft, topLeft)
        )

        assertEquals(
            PageCorners(
                topLeft = topLeft,
                topRight = topRight,
                bottomRight = bottomRight,
                bottomLeft = bottomLeft
            ),
            orderedCorners
        )
    }

    @Test
    fun orderCornersReturnsNullForInvalidCornerCount() {
        val orderedCorners = CornerOrderingUtil.orderCorners(
            listOf(
                PointFSerializable(20f, 30f),
                PointFSerializable(180f, 40f),
                PointFSerializable(170f, 260f)
            )
        )

        assertNull(orderedCorners)
    }

    @Test
    fun orderCornersReturnsNullForDuplicatePoints() {
        val duplicatePoint = PointFSerializable(20f, 30f)

        val orderedCorners = CornerOrderingUtil.orderCorners(
            listOf(
                duplicatePoint,
                PointFSerializable(180f, 40f),
                PointFSerializable(170f, 260f),
                duplicatePoint
            )
        )

        assertNull(orderedCorners)
    }

    @Test
    fun orderCornersReturnsNullForNonFiniteCoordinates() {
        val orderedCorners = CornerOrderingUtil.orderCorners(
            listOf(
                PointFSerializable(20f, 30f),
                PointFSerializable(Float.NaN, 40f),
                PointFSerializable(170f, 260f),
                PointFSerializable(25f, Float.POSITIVE_INFINITY)
            )
        )

        assertNull(orderedCorners)
    }

    @Test
    fun scaledByScalesEveryCornerIndependently() {
        val corners = PageCorners(
            topLeft = PointFSerializable(1f, 2f),
            topRight = PointFSerializable(3f, 4f),
            bottomRight = PointFSerializable(5f, 6f),
            bottomLeft = PointFSerializable(7f, 8f)
        )

        assertEquals(
            PageCorners(
                topLeft = PointFSerializable(2f, 6f),
                topRight = PointFSerializable(6f, 12f),
                bottomRight = PointFSerializable(10f, 18f),
                bottomLeft = PointFSerializable(14f, 24f)
            ),
            corners.scaledBy(scaleX = 2f, scaleY = 3f)
        )
    }
}
