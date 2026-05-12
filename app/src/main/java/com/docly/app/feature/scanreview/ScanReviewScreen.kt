package com.docly.app.feature.scanreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.docly.app.domain.usecase.scanner.ScannedOutputFormat
import com.docly.app.ui.components.DoclyAdaptiveLayout
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLazyScreenScaffold
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ScanReviewScreen(
    uiState: ScanReviewUiState,
    onEvent: (ScanReviewUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyLazyScreenScaffold(
        title = "Review scan",
        screenTestTag = DoclyTestTags.REVIEW_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        lazyListTestTag = DoclyTestTags.EDITOR_PAGE_LIST
    ) {
        item {
            ScanTitleField(uiState = uiState, onEvent = onEvent)
        }
        item {
            OutputFormatSelector(uiState = uiState, onEvent = onEvent)
        }
        item {
            ReviewActions(uiState = uiState, onEvent = onEvent)
        }
        if (uiState.errorMessage != null && !uiState.isLoading && uiState.pages.isNotEmpty()) {
            item {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        when {
            uiState.isLoading -> item {
                DoclyLoadingContent(message = "Loading scanned pages...")
            }

            uiState.errorMessage != null && uiState.pages.isEmpty() -> item {
                DoclyErrorContent(
                    title = "Could not load scan",
                    message = uiState.errorMessage
                )
            }

            uiState.pages.isEmpty() -> item {
                DoclyEmptyContent(
                    title = "No scanned pages",
                    message = "Add at least one page before saving.",
                    actionLabel = "Add page",
                    onAction = { onEvent(ScanReviewUiEvent.OnAddPageClicked) }
                )
            }

            else -> scanPageItems(
                pages = uiState.pages,
                canEditPages = uiState.canEditPages,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun ScanTitleField(uiState: ScanReviewUiState, onEvent: (ScanReviewUiEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.title,
        onValueChange = { title -> onEvent(ScanReviewUiEvent.OnTitleChanged(title)) },
        enabled = !uiState.isSaving,
        label = { Text(text = "Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OutputFormatSelector(uiState: ScanReviewUiState, onEvent: (ScanReviewUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = uiState.outputFormat == ScannedOutputFormat.PDF,
            onClick = { onEvent(ScanReviewUiEvent.OnOutputFormatChanged(ScannedOutputFormat.PDF)) },
            enabled = !uiState.isSaving,
            label = { Text(text = "PDF") }
        )
        FilterChip(
            selected = uiState.outputFormat == ScannedOutputFormat.IMAGES,
            onClick = { onEvent(ScanReviewUiEvent.OnOutputFormatChanged(ScannedOutputFormat.IMAGES)) },
            enabled = !uiState.isSaving,
            label = { Text(text = "Images") }
        )
    }
}

@Composable
private fun ReviewActions(uiState: ScanReviewUiState, onEvent: (ScanReviewUiEvent) -> Unit) {
    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ScanReviewUiEvent.OnAddPageClicked) },
                enabled = uiState.canEditPages,
                modifier = actionModifier.testTag(DoclyTestTags.EDITOR_ADD_PAGE_ACTION)
            ) {
                Text(text = "Add page")
            }
        },
        second = { actionModifier ->
            Button(
                onClick = { onEvent(ScanReviewUiEvent.OnSaveClicked) },
                enabled = uiState.canSave,
                modifier = actionModifier.testTag(DoclyTestTags.EXPORT_PDF_ACTION)
            ) {
                Text(text = if (uiState.isSaving) "Saving..." else "Save")
            }
        }
    )
}

private fun LazyListScope.scanPageItems(
    pages: List<ScannedPage>,
    canEditPages: Boolean,
    onEvent: (ScanReviewUiEvent) -> Unit
) {
    itemsIndexed(
        items = pages,
        key = { _, page -> page.id }
    ) { index, page ->
        ScanPageRow(
            page = page,
            pageNumber = index + 1,
            isFirstPage = index == 0,
            isLastPage = index == pages.lastIndex,
            canEditPages = canEditPages,
            onEvent = onEvent
        )
    }
}

@Composable
private fun ScanPageRow(
    page: ScannedPage,
    pageNumber: Int,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    canEditPages: Boolean,
    onEvent: (ScanReviewUiEvent) -> Unit
) {
    DoclyAdaptiveLayout {
        if (it) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 152.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScanPageThumbnail(page = page, pageNumber = pageNumber)
                ScanPageDetails(
                    page = page,
                    pageNumber = pageNumber,
                    isFirstPage = isFirstPage,
                    isLastPage = isLastPage,
                    canEditPages = canEditPages,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 152.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScanPageThumbnail(page = page, pageNumber = pageNumber)
                ScanPageDetails(
                    page = page,
                    pageNumber = pageNumber,
                    isFirstPage = isFirstPage,
                    isLastPage = isLastPage,
                    canEditPages = canEditPages,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScanPageThumbnail(page: ScannedPage, pageNumber: Int) {
    DoclyImageThumbnail(
        imagePath = page.thumbnailPath ?: page.processedImagePath ?: page.originalImagePath,
        contentDescription = "Page $pageNumber thumbnail",
        modifier = Modifier
            .size(width = 84.dp, height = 112.dp)
            .graphicsLayer {
                rotationZ = page.rotationDegrees.toFloat()
            },
        testTag = DoclyTestTags.REVIEW_PAGE_THUMBNAIL,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun ScanPageDetails(
    page: ScannedPage,
    pageNumber: Int,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    canEditPages: Boolean,
    onEvent: (ScanReviewUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
        DoclyAdaptiveTwoActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ScanReviewUiEvent.OnMovePageUp(page.id)) },
                    enabled = canEditPages && !isFirstPage,
                    modifier = actionModifier.testTag(DoclyTestTags.EDITOR_MOVE_PAGE_UP_ACTION)
                ) {
                    Text(text = "Up")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ScanReviewUiEvent.OnMovePageDown(page.id)) },
                    enabled = canEditPages && !isLastPage,
                    modifier = actionModifier.testTag(DoclyTestTags.EDITOR_MOVE_PAGE_DOWN_ACTION)
                ) {
                    Text(text = "Down")
                }
            }
        )
        DoclyAdaptiveTwoActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ScanReviewUiEvent.OnRotatePageClicked(page.id)) },
                    enabled = canEditPages,
                    modifier = actionModifier.testTag(DoclyTestTags.EDITOR_ROTATE_PAGE_ACTION)
                ) {
                    Text(text = "Rotate")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ScanReviewUiEvent.OnDeletePageClicked(page.id)) },
                    enabled = canEditPages,
                    modifier = actionModifier.testTag(DoclyTestTags.EDITOR_DELETE_PAGE_ACTION)
                ) {
                    Text(text = "Delete")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScanReviewScreenPreview() {
    DoclyTheme {
        ScanReviewScreen(
            uiState = ScanReviewUiState(
                sessionId = PLACEHOLDER_SESSION_ID,
                pages = listOf(
                    ScannedPage(
                        id = "page-1",
                        sessionId = PLACEHOLDER_SESSION_ID,
                        pageIndex = 0,
                        originalImagePath = "/raw/page.jpg",
                        processedImagePath = "/raw/page.jpg",
                        thumbnailPath = "/thumb/page.jpg",
                        rotationDegrees = 0,
                        scanMode = ScanMode.DOCUMENT,
                        width = 1200,
                        height = 1600,
                        createdAt = 0L
                    )
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
