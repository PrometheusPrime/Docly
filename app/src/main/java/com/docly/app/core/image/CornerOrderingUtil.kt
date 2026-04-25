package com.docly.app.core.image

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import kotlin.math.atan2

object CornerOrderingUtil {
    fun orderCorners(points: List<PointFSerializable>): PageCorners? {
        if (points.size != EXPECTED_CORNER_COUNT) return null
        if (points.any { point -> !point.hasFiniteCoordinates() }) return null
        if (points.distinct().size != EXPECTED_CORNER_COUNT) return null

        val centerX = points.sumOf { point -> point.x.toDouble() } / EXPECTED_CORNER_COUNT
        val centerY = points.sumOf { point -> point.y.toDouble() } / EXPECTED_CORNER_COUNT
        val clockwiseCorners = points.sortedBy { point ->
            atan2(
                y = (point.y - centerY).toDouble(),
                x = (point.x - centerX).toDouble()
            )
        }
        val topLeftIndex = clockwiseCorners.indexOfMinBy { point -> point.x + point.y }
        if (topLeftIndex < 0) return null

        val ordered = clockwiseCorners.rotateLeft(topLeftIndex)
        return PageCorners(
            topLeft = ordered[0],
            topRight = ordered[1],
            bottomRight = ordered[2],
            bottomLeft = ordered[3]
        )
    }

    private fun PointFSerializable.hasFiniteCoordinates(): Boolean =
        !x.isNaN() && !x.isInfinite() && !y.isNaN() && !y.isInfinite()

    private inline fun List<PointFSerializable>.indexOfMinBy(selector: (PointFSerializable) -> Float): Int {
        if (isEmpty()) return -1

        var minIndex = 0
        var minValue = selector(this[0])
        for (index in 1 until size) {
            val value = selector(this[index])
            if (value < minValue) {
                minIndex = index
                minValue = value
            }
        }
        return minIndex
    }

    private fun List<PointFSerializable>.rotateLeft(startIndex: Int): List<PointFSerializable> {
        if (startIndex == 0) return this
        return drop(startIndex) + take(startIndex)
    }

    private const val EXPECTED_CORNER_COUNT = 4
}

fun PageCorners.scaledBy(scaleX: Float, scaleY: Float): PageCorners = PageCorners(
    topLeft = topLeft.scaledBy(scaleX = scaleX, scaleY = scaleY),
    topRight = topRight.scaledBy(scaleX = scaleX, scaleY = scaleY),
    bottomRight = bottomRight.scaledBy(scaleX = scaleX, scaleY = scaleY),
    bottomLeft = bottomLeft.scaledBy(scaleX = scaleX, scaleY = scaleY)
)

private fun PointFSerializable.scaledBy(scaleX: Float, scaleY: Float): PointFSerializable = PointFSerializable(
    x = x * scaleX,
    y = y * scaleY
)
