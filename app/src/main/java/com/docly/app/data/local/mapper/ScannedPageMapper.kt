package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.ScannedPageEntity
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ScannedPage

fun ScannedPageEntity.toDomain(): ScannedPage = ScannedPage(
    id = id,
    sessionId = sessionId,
    pageIndex = pageIndex,
    originalImagePath = originalImagePath,
    processedImagePath = processedImagePath,
    thumbnailPath = thumbnailPath,
    rotationDegrees = rotationDegrees,
    scanMode = scanMode.toScanMode(),
    width = width,
    height = height,
    corners = toCornersOrNull(),
    createdAt = createdAt,
    reviewStatus = reviewStatus.toPageReviewStatus()
)

fun ScannedPage.toEntity(): ScannedPageEntity = ScannedPageEntity(
    id = id,
    sessionId = sessionId,
    pageIndex = pageIndex,
    originalImagePath = originalImagePath,
    processedImagePath = processedImagePath,
    thumbnailPath = thumbnailPath,
    rotationDegrees = rotationDegrees,
    scanMode = scanMode.name,
    reviewStatus = reviewStatus.name,
    width = width,
    height = height,
    topLeftX = corners?.topLeft?.x,
    topLeftY = corners?.topLeft?.y,
    topRightX = corners?.topRight?.x,
    topRightY = corners?.topRight?.y,
    bottomRightX = corners?.bottomRight?.x,
    bottomRightY = corners?.bottomRight?.y,
    bottomLeftX = corners?.bottomLeft?.x,
    bottomLeftY = corners?.bottomLeft?.y,
    createdAt = createdAt
)

private fun ScannedPageEntity.toCornersOrNull(): PageCorners? {
    val topLeftPoint = pointOrNull(topLeftX, topLeftY) ?: return null
    val topRightPoint = pointOrNull(topRightX, topRightY) ?: return null
    val bottomRightPoint = pointOrNull(bottomRightX, bottomRightY) ?: return null
    val bottomLeftPoint = pointOrNull(bottomLeftX, bottomLeftY) ?: return null

    return PageCorners(
        topLeft = topLeftPoint,
        topRight = topRightPoint,
        bottomRight = bottomRightPoint,
        bottomLeft = bottomLeftPoint
    )
}

private fun pointOrNull(x: Float?, y: Float?): PointFSerializable? = if (x != null && y != null) {
    PointFSerializable(x = x, y = y)
} else {
    null
}
