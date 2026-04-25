package com.docly.app.feature.review

import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable

data class CropPoint(val x: Float, val y: Float)

data class CropDelta(val x: Float, val y: Float)

enum class CropCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

data class CropCoordinateMapper(
    val imageWidth: Float,
    val imageHeight: Float,
    val viewportWidth: Float,
    val viewportHeight: Float
) {
    val scale: Float = minOf(viewportWidth / imageWidth, viewportHeight / imageHeight)
    val horizontalOffset: Float = (viewportWidth - imageWidth * scale) / 2f
    val verticalOffset: Float = (viewportHeight - imageHeight * scale) / 2f
    private val maxImageX: Float = (imageWidth - 1f).coerceAtLeast(0f)
    private val maxImageY: Float = (imageHeight - 1f).coerceAtLeast(0f)

    init {
        require(imageWidth > 0f) { "Image width must be positive." }
        require(imageHeight > 0f) { "Image height must be positive." }
        require(viewportWidth > 0f) { "Viewport width must be positive." }
        require(viewportHeight > 0f) { "Viewport height must be positive." }
    }

    fun imageToViewport(point: PointFSerializable): CropPoint {
        val clampedPoint = point.clamped()
        return CropPoint(
            x = clampedPoint.x * scale + horizontalOffset,
            y = clampedPoint.y * scale + verticalOffset
        )
    }

    fun viewportToImage(point: CropPoint): PointFSerializable = PointFSerializable(
        x = ((point.x - horizontalOffset) / scale).coerceIn(0f, maxImageX),
        y = ((point.y - verticalOffset) / scale).coerceIn(0f, maxImageY)
    )

    fun viewportDeltaToImageDelta(deltaX: Float, deltaY: Float): CropDelta = CropDelta(
        x = deltaX / scale,
        y = deltaY / scale
    )

    fun moveCornerByViewportDelta(corners: PageCorners, corner: CropCorner, deltaX: Float, deltaY: Float): PageCorners {
        val delta = viewportDeltaToImageDelta(deltaX = deltaX, deltaY = deltaY)
        val movedPoint = corners.pointFor(corner).moveBy(delta).clamped()
        return corners.withCorner(corner = corner, point = movedPoint)
    }

    private fun PointFSerializable.moveBy(delta: CropDelta): PointFSerializable = PointFSerializable(
        x = x + delta.x,
        y = y + delta.y
    )

    private fun PointFSerializable.clamped(): PointFSerializable = PointFSerializable(
        x = x.coerceIn(0f, maxImageX),
        y = y.coerceIn(0f, maxImageY)
    )

    companion object {
        fun create(imageWidth: Int, imageHeight: Int, viewportWidth: Int, viewportHeight: Int): CropCoordinateMapper? {
            if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
                return null
            }
            return CropCoordinateMapper(
                imageWidth = imageWidth.toFloat(),
                imageHeight = imageHeight.toFloat(),
                viewportWidth = viewportWidth.toFloat(),
                viewportHeight = viewportHeight.toFloat()
            )
        }
    }
}

fun fullImageCorners(imageWidth: Int, imageHeight: Int): PageCorners? {
    if (imageWidth <= 0 || imageHeight <= 0) return null

    val maxX = (imageWidth - 1).coerceAtLeast(0).toFloat()
    val maxY = (imageHeight - 1).coerceAtLeast(0).toFloat()
    return PageCorners(
        topLeft = PointFSerializable(0f, 0f),
        topRight = PointFSerializable(maxX, 0f),
        bottomRight = PointFSerializable(maxX, maxY),
        bottomLeft = PointFSerializable(0f, maxY)
    )
}

fun PageCorners.pointFor(corner: CropCorner): PointFSerializable = when (corner) {
    CropCorner.TOP_LEFT -> topLeft
    CropCorner.TOP_RIGHT -> topRight
    CropCorner.BOTTOM_RIGHT -> bottomRight
    CropCorner.BOTTOM_LEFT -> bottomLeft
}

fun PageCorners.withCorner(corner: CropCorner, point: PointFSerializable): PageCorners = when (corner) {
    CropCorner.TOP_LEFT -> copy(topLeft = point)
    CropCorner.TOP_RIGHT -> copy(topRight = point)
    CropCorner.BOTTOM_RIGHT -> copy(bottomRight = point)
    CropCorner.BOTTOM_LEFT -> copy(bottomLeft = point)
}
