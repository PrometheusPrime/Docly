package com.docly.app.feature.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.docly.app.domain.model.PageCorners
import com.docly.app.ui.util.DoclyTestTags
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CropAdjustmentPreview(
    imagePath: String,
    imageWidth: Int,
    imageHeight: Int,
    corners: PageCorners,
    onCornersChanged: (PageCorners) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val mapper = remember(imageWidth, imageHeight, viewportSize) {
        CropCoordinateMapper.create(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewportWidth = viewportSize.width,
            viewportHeight = viewportSize.height
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { size -> viewportSize = size }
    ) {
        AsyncImage(
            model = File(imagePath),
            contentDescription = "Page crop preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (mapper != null) {
            CropPolygon(corners = corners, mapper = mapper)
            CropCorner.entries.forEach { corner ->
                CropCornerHandle(
                    corner = corner,
                    corners = corners,
                    mapper = mapper,
                    isEnabled = isEnabled,
                    onCornersChanged = onCornersChanged
                )
            }
        }
    }
}

@Composable
private fun CropPolygon(corners: PageCorners, mapper: CropCoordinateMapper) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DoclyTestTags.REVIEW_CROP_OVERLAY)
    ) {
        val topLeft = mapper.imageToViewport(corners.topLeft)
        val topRight = mapper.imageToViewport(corners.topRight)
        val bottomRight = mapper.imageToViewport(corners.bottomRight)
        val bottomLeft = mapper.imageToViewport(corners.bottomLeft)
        val path = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        drawPath(
            path = path,
            color = Color(0xFF69D6C5),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun CropCornerHandle(
    corner: CropCorner,
    corners: PageCorners,
    mapper: CropCoordinateMapper,
    isEnabled: Boolean,
    onCornersChanged: (PageCorners) -> Unit
) {
    val density = LocalDensity.current
    val handleSizePx = with(density) { CropHandleSize.roundToPx() }
    val point = mapper.imageToViewport(corners.pointFor(corner))
    val dragModifier = if (isEnabled) {
        Modifier.pointerInput(corner, corners, mapper) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                onCornersChanged(
                    mapper.moveCornerByViewportDelta(
                        corners = corners,
                        corner = corner,
                        deltaX = dragAmount.x,
                        deltaY = dragAmount.y
                    )
                )
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = point.x.roundToInt() - handleSizePx / 2,
                    y = point.y.roundToInt() - handleSizePx / 2
                )
            }
            .size(CropHandleSize)
            .then(dragModifier)
            .semantics {
                contentDescription = corner.contentDescription
            }
            .testTag(corner.testTag)
            .border(width = 2.dp, color = Color.White, shape = CircleShape)
            .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
    )
}

private val CropHandleSize = 48.dp

private val CropCorner.contentDescription: String
    get() = when (this) {
        CropCorner.TOP_LEFT -> "Top left crop corner"
        CropCorner.TOP_RIGHT -> "Top right crop corner"
        CropCorner.BOTTOM_RIGHT -> "Bottom right crop corner"
        CropCorner.BOTTOM_LEFT -> "Bottom left crop corner"
    }

private val CropCorner.testTag: String
    get() = when (this) {
        CropCorner.TOP_LEFT -> DoclyTestTags.REVIEW_CROP_TOP_LEFT_HANDLE
        CropCorner.TOP_RIGHT -> DoclyTestTags.REVIEW_CROP_TOP_RIGHT_HANDLE
        CropCorner.BOTTOM_RIGHT -> DoclyTestTags.REVIEW_CROP_BOTTOM_RIGHT_HANDLE
        CropCorner.BOTTOM_LEFT -> DoclyTestTags.REVIEW_CROP_BOTTOM_LEFT_HANDLE
    }
