package com.docly.app.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.ScanModeSelector
import com.docly.app.ui.components.doclyMinimumTouchTarget
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
    var showRescanConfirmation by rememberSaveable { mutableStateOf(false) }

    DoclyScreenScaffold(
        title = "Review",
        screenTestTag = DoclyTestTags.REVIEW_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Review page",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${uiState.pendingPageCount} pending, ${uiState.acceptedPageCount} accepted",
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
        ReviewProgressMessage(uiState = uiState)
        if (uiState.currentPageId != null) {
            ScanModeSelector(
                selectedScanMode = uiState.selectedScanMode,
                onScanModeSelected = { scanMode -> onEvent(ReviewUiEvent.OnScanModeChanged(scanMode)) },
                enabled = uiState.canSelectScanMode
            )
        }
        ReviewPreview(uiState = uiState, onEvent = onEvent)
        if (uiState.currentPageId != null) {
            QualityWarning(
                uiState = uiState,
                onEvent = onEvent,
                onRequestRescan = {
                    showRescanConfirmation = true
                }
            )
            ReviewPreviewActions(uiState = uiState, onEvent = onEvent)
            CropActions(uiState = uiState, onEvent = onEvent)
            ReviewDecisionActions(
                uiState = uiState,
                onEvent = onEvent,
                onRequestRescan = {
                    showRescanConfirmation = true
                }
            )
        } else {
            DoclyEmptyContent(
                title = "Page preview pending",
                message = "Captured or imported pages will appear here."
            )
        }
        if (uiState.acceptedPageCount > 0) {
            OutlinedButton(
                onClick = onEditPages,
                modifier = Modifier
                    .testTag(DoclyTestTags.REVIEW_EDITOR_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Edit accepted pages")
            }
        }
    }

    RescanConfirmationDialog(
        showDialog = showRescanConfirmation && uiState.currentPageId != null,
        isSaving = uiState.isSaving,
        onConfirm = {
            showRescanConfirmation = false
            onEvent(ReviewUiEvent.OnRescanClicked)
        },
        onDismiss = {
            showRescanConfirmation = false
        }
    )
}

@Composable
private fun ReviewProgressMessage(uiState: ReviewUiState) {
    val message = when {
        uiState.isProcessing -> "Processing page..."
        uiState.isSaving -> "Saving page..."
        else -> return
    }
    DoclyLoadingContent(message = message)
}

@Composable
private fun RescanConfirmationDialog(
    showDialog: Boolean,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = { Text(text = "Rescan this page?") },
        text = {
            Text(text = "This removes the pending page and returns to scanning.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier
                    .testTag(DoclyTestTags.REVIEW_RESCAN_CONFIRM_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Rescan")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .testTag(DoclyTestTags.REVIEW_RESCAN_DISMISS_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier
            .testTag(DoclyTestTags.REVIEW_RESCAN_DIALOG)
            .semantics {
                contentDescription = "Rescan page confirmation"
            }
    )
}

@Composable
private fun QualityWarning(uiState: ReviewUiState, onEvent: (ReviewUiEvent) -> Unit, onRequestRescan: () -> Unit) {
    val warning = uiState.qualityWarning ?: return
    if (uiState.isQualityWarningAcknowledged) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.REVIEW_QUALITY_WARNING),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Scan warning",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = warning.message,
                style = MaterialTheme.typography.bodyMedium
            )
            DoclyAdaptiveTwoActionRow(
                first = { actionModifier ->
                    Button(
                        onClick = { onEvent(ReviewUiEvent.OnContinueWithLowQualityClicked) },
                        enabled = !uiState.isProcessing && !uiState.isSaving,
                        modifier = actionModifier.testTag(DoclyTestTags.REVIEW_QUALITY_CONTINUE_ACTION)
                    ) {
                        Text(text = "Continue")
                    }
                },
                second = { actionModifier ->
                    OutlinedButton(
                        onClick = onRequestRescan,
                        enabled = uiState.canDiscardForRescan,
                        modifier = actionModifier.testTag(DoclyTestTags.REVIEW_QUALITY_RESCAN_ACTION)
                    ) {
                        Text(text = "Rescan")
                    }
                }
            )
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

    val previewPath = if (uiState.showOriginal) {
        uiState.rawImagePath.takeIf { path -> path.isNotBlank() }
    } else {
        uiState.processedImagePath
            ?: uiState.thumbnailPath
            ?: uiState.rawImagePath.takeIf { path -> path.isNotBlank() }
    }
    if (previewPath != null) {
        DoclyImageThumbnail(
            imagePath = previewPath,
            contentDescription = if (uiState.showOriginal) {
                "Original page preview"
            } else {
                "Processed page preview"
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 420.dp)
                .graphicsLayer {
                    rotationZ = uiState.rotationDegrees.toFloat()
                },
            testTag = DoclyTestTags.REVIEW_PAGE_THUMBNAIL,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun ReviewPreviewActions(uiState: ReviewUiState, onEvent: (ReviewUiEvent) -> Unit) {
    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReviewUiEvent.OnToggleOriginalClicked) },
                enabled = uiState.canToggleOriginal,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_TOGGLE_ORIGINAL_ACTION)
            ) {
                Text(text = if (uiState.showOriginal) "Processed" else "Original")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReviewUiEvent.OnToggleCropEditorClicked) },
                enabled = uiState.canAdjustCrop && !uiState.isProcessing && !uiState.isSaving,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_TOGGLE_CROP_ACTION)
            ) {
                Text(text = if (uiState.isCropAdjustmentVisible) "Close crop" else "Adjust crop")
            }
        }
    )
}

@Composable
private fun CropActions(uiState: ReviewUiState, onEvent: (ReviewUiEvent) -> Unit) {
    if (!uiState.isCropAdjustmentVisible) return

    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReviewUiEvent.OnResetToDetectedClicked) },
                enabled = uiState.canResetToDetected,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_RESET_DETECTED_ACTION)
            ) {
                Text(text = "Detected")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReviewUiEvent.OnResetToFullImageClicked) },
                enabled = uiState.canResetToFullImage,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_RESET_FULL_IMAGE_ACTION)
            ) {
                Text(text = "Full image")
            }
        }
    )
    Button(
        onClick = { onEvent(ReviewUiEvent.OnReprocessClicked) },
        enabled = uiState.canApplyCrop,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.REVIEW_APPLY_CROP_ACTION)
            .doclyMinimumTouchTarget()
    ) {
        Text(text = uiState.applyActionLabel)
    }
}

@Composable
private fun ReviewDecisionActions(
    uiState: ReviewUiState,
    onEvent: (ReviewUiEvent) -> Unit,
    onRequestRescan: () -> Unit
) {
    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReviewUiEvent.OnRotateClicked) },
                enabled = uiState.canRotate,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_ROTATE_ACTION)
            ) {
                Text(text = "Rotate")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = onRequestRescan,
                enabled = uiState.canDiscardForRescan,
                modifier = actionModifier.testTag(DoclyTestTags.REVIEW_RESCAN_ACTION)
            ) {
                Text(text = "Rescan")
            }
        }
    )
    Button(
        onClick = { onEvent(ReviewUiEvent.OnAcceptClicked) },
        enabled = uiState.canAccept,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.REVIEW_ACCEPT_ACTION)
            .doclyMinimumTouchTarget()
    ) {
        Text(text = if (uiState.isSaving) "Saving..." else "Accept page")
    }
}

private val ReviewUiState.applyActionLabel: String
    get() = when {
        isProcessing -> "Applying..."
        hasPendingScanModeChange -> "Apply mode"
        hasPendingCropChange -> "Apply crop"
        else -> "Apply crop"
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
                appliedCorners = previewCorners(),
                editableCorners = previewCorners(),
                pendingPageCount = 1
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
