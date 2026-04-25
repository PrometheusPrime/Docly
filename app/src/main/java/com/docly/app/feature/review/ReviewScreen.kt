package com.docly.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onEvent: (ReviewUiEvent) -> Unit,
    onEditPages: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Review",
        screenTestTag = DoclyTestTags.REVIEW_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Review pages",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Session ID: ${uiState.sessionId}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        ReviewPreview(uiState = uiState, onEvent = onEvent)
        if (uiState.currentPageId != null) {
            CropActions(uiState = uiState, onEvent = onEvent)
        } else {
            DoclyEmptyContent(
                title = "Page preview pending",
                message = "Captured or imported pages will appear here."
            )
        }
        Button(
            onClick = onEditPages,
            modifier = Modifier.testTag(DoclyTestTags.REVIEW_EDITOR_ACTION)
        ) {
            Text(text = "Edit pages")
        }
    }
}

@Composable
private fun ReviewPreview(uiState: ReviewUiState, onEvent: (ReviewUiEvent) -> Unit) {
    val editableCorners = uiState.editableCorners
    if (uiState.hasCropEditor && editableCorners != null) {
        CropAdjustmentPreview(
            imagePath = uiState.rawImagePath,
            imageWidth = uiState.imageWidth,
            imageHeight = uiState.imageHeight,
            corners = editableCorners,
            onCornersChanged = { corners -> onEvent(ReviewUiEvent.OnCornersChanged(corners)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .testTag(DoclyTestTags.REVIEW_PAGE_THUMBNAIL),
            isEnabled = uiState.canApplyCrop
        )
        return
    }

    val previewPath = uiState.thumbnailPath
        ?: uiState.processedImagePath
        ?: uiState.rawImagePath.takeIf { path -> path.isNotBlank() }
    if (previewPath != null) {
        DoclyImageThumbnail(
            imagePath = previewPath,
            contentDescription = "Latest scanned page preview",
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            testTag = DoclyTestTags.REVIEW_PAGE_THUMBNAIL,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun CropActions(uiState: ReviewUiState, onEvent: (ReviewUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { onEvent(ReviewUiEvent.OnResetToDetectedClicked) },
            enabled = uiState.canResetToDetected,
            modifier = Modifier
                .weight(1f)
                .testTag(DoclyTestTags.REVIEW_RESET_DETECTED_ACTION)
        ) {
            Text(text = "Detected")
        }
        OutlinedButton(
            onClick = { onEvent(ReviewUiEvent.OnResetToFullImageClicked) },
            enabled = uiState.canResetToFullImage,
            modifier = Modifier
                .weight(1f)
                .testTag(DoclyTestTags.REVIEW_RESET_FULL_IMAGE_ACTION)
        ) {
            Text(text = "Full image")
        }
    }
    Button(
        onClick = { onEvent(ReviewUiEvent.OnReprocessClicked) },
        enabled = uiState.canApplyCrop,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.REVIEW_APPLY_CROP_ACTION)
    ) {
        Text(text = if (uiState.isProcessing) "Applying..." else "Apply crop")
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    DoclyTheme {
        ReviewScreen(
            uiState = ReviewUiState(
                sessionId = PLACEHOLDER_SESSION_ID,
                currentPageId = "page-id",
                rawImagePath = "/raw/page.jpg",
                imageWidth = 1000,
                imageHeight = 1400,
                detectedCorners = previewCorners(),
                editableCorners = previewCorners()
            ),
            onEvent = {},
            onEditPages = {},
            onNavigateBack = {}
        )
    }
}

private fun previewCorners(): PageCorners = PageCorners(
    topLeft = PointFSerializable(90f, 120f),
    topRight = PointFSerializable(900f, 110f),
    bottomRight = PointFSerializable(870f, 1280f),
    bottomLeft = PointFSerializable(120f, 1300f)
)
