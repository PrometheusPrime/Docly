package com.docly.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScannedPage
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    onEvent: (EditorUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Editor",
        screenTestTag = DoclyTestTags.EDITOR_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Arrange pages",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Session ID: ${uiState.sessionId}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.pendingPageCount > 0) {
            Text(
                text = "${uiState.pendingPageCount} pending page(s) still need review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (uiState.errorMessage != null && !uiState.isLoading && uiState.pages.isNotEmpty()) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        EditorSessionActions(uiState = uiState, onEvent = onEvent)
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading pages...")

            uiState.errorMessage != null && uiState.pages.isEmpty() -> DoclyErrorContent(
                title = "Could not load pages",
                message = uiState.errorMessage
            )

            uiState.pages.isEmpty() -> DoclyEmptyContent(
                title = "No editable pages",
                message = "Page ordering, rotation, and deletion controls will appear here."
            )

            else -> EditorPageList(
                pages = uiState.pages,
                canEditPages = uiState.canEditPages,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun EditorSessionActions(uiState: EditorUiState, onEvent: (EditorUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { onEvent(EditorUiEvent.OnAddPageClicked) },
            enabled = uiState.canEditPages,
            modifier = Modifier
                .weight(1f)
                .testTag(DoclyTestTags.EDITOR_ADD_PAGE_ACTION)
        ) {
            Text(text = "Add page")
        }
        Button(
            onClick = { onEvent(EditorUiEvent.OnContinueClicked) },
            enabled = uiState.canContinue,
            modifier = Modifier
                .weight(1f)
                .testTag(DoclyTestTags.EDITOR_CONTINUE_ACTION)
        ) {
            Text(text = if (uiState.isSaving) "Saving..." else "Continue")
        }
    }
}

@Composable
private fun EditorPageList(pages: List<ScannedPage>, canEditPages: Boolean, onEvent: (EditorUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pages.forEachIndexed { index, page ->
            EditorPageRow(
                page = page,
                pageNumber = index + 1,
                isFirstPage = index == 0,
                isLastPage = index == pages.lastIndex,
                canEditPages = canEditPages,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun EditorPageRow(
    page: ScannedPage,
    pageNumber: Int,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    canEditPages: Boolean,
    onEvent: (EditorUiEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DoclyImageThumbnail(
            imagePath = page.thumbnailPath ?: page.processedImagePath ?: page.originalImagePath,
            contentDescription = "Page $pageNumber thumbnail",
            modifier = Modifier
                .size(width = 84.dp, height = 112.dp)
                .graphicsLayer {
                    rotationZ = page.rotationDegrees.toFloat()
                },
            testTag = DoclyTestTags.EDITOR_PAGE_THUMBNAIL,
            contentScale = ContentScale.Fit
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Page $pageNumber",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${page.width} x ${page.height}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEvent(EditorUiEvent.OnMovePageUp(page.id)) },
                    enabled = canEditPages && !isFirstPage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.EDITOR_MOVE_PAGE_UP_ACTION)
                ) {
                    Text(text = "Up")
                }
                OutlinedButton(
                    onClick = { onEvent(EditorUiEvent.OnMovePageDown(page.id)) },
                    enabled = canEditPages && !isLastPage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.EDITOR_MOVE_PAGE_DOWN_ACTION)
                ) {
                    Text(text = "Down")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEvent(EditorUiEvent.OnRotatePageClicked(page.id)) },
                    enabled = canEditPages,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.EDITOR_ROTATE_PAGE_ACTION)
                ) {
                    Text(text = "Rotate")
                }
                OutlinedButton(
                    onClick = { onEvent(EditorUiEvent.OnDeletePageClicked(page.id)) },
                    enabled = canEditPages,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.EDITOR_DELETE_PAGE_ACTION)
                ) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScreenPreview() {
    DoclyTheme {
        EditorScreen(
            uiState = EditorUiState(
                sessionId = PLACEHOLDER_SESSION_ID,
                pages = listOf(
                    ScannedPage(
                        id = "page-id",
                        sessionId = PLACEHOLDER_SESSION_ID,
                        pageIndex = 0,
                        originalImagePath = "/raw/page.jpg",
                        processedImagePath = null,
                        thumbnailPath = "/thumb/page.jpg",
                        rotationDegrees = 0,
                        scanMode = ScanMode.DOCUMENT,
                        width = 1000,
                        height = 1400,
                        createdAt = 1L
                    )
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
