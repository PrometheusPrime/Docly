package com.docly.app.feature.pdfpageeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScannedPage
import com.docly.app.ui.components.DoclyAdaptiveLayout
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLazyScreenScaffold
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun PdfPageEditorScreen(
    uiState: PdfPageEditorUiState,
    onEvent: (PdfPageEditorUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeletePageId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        onEvent(PdfPageEditorUiEvent.OnLoad)
    }

    DoclyLazyScreenScaffold(
        title = uiState.title,
        screenTestTag = DoclyTestTags.PDF_PAGE_EDITOR_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        lazyListTestTag = DoclyTestTags.PDF_PAGE_EDITOR_PAGE_LIST
    ) {
        item {
            Text(
                text = "PDF page tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item {
            Text(
                text = "Reorder, rotate, or remove retained scan pages.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        item {
            Button(
                onClick = { onEvent(PdfPageEditorUiEvent.OnSaveClicked) },
                enabled = uiState.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.PDF_PAGE_EDITOR_SAVE_ACTION)
            ) {
                Text(text = if (uiState.isSaving) "Updating..." else "Update PDF")
            }
        }
        when {
            uiState.isLoading -> item {
                DoclyLoadingContent(message = "Loading pages...")
            }

            uiState.errorMessage != null && uiState.pages.isEmpty() -> item {
                DoclyErrorContent(
                    title = "Could not load page tools",
                    message = uiState.errorMessage
                )
            }

            uiState.pages.isEmpty() -> item {
                DoclyEmptyContent(
                    title = "No retained pages",
                    message = "This PDF does not have scan-source pages available for editing."
                )
            }

            else -> pdfPageItems(
                pages = uiState.pages,
                canEditPages = uiState.canEditPages,
                onEvent = onEvent,
                onDeletePage = { pageId -> pendingDeletePageId = pageId }
            )
        }
    }

    val pendingDeletePageNumber = pendingDeletePageId?.let { pageId ->
        uiState.pages.indexOfFirst { page -> page.id == pageId }.takeIf { index -> index >= 0 }?.plus(1)
    }
    DeletePdfPageConfirmationDialog(
        pageNumber = pendingDeletePageNumber,
        isSaving = uiState.isSaving,
        onConfirm = {
            pendingDeletePageId?.let { pageId ->
                pendingDeletePageId = null
                onEvent(PdfPageEditorUiEvent.OnDeletePageClicked(pageId))
            }
        },
        onDismiss = { pendingDeletePageId = null }
    )
}

private fun LazyListScope.pdfPageItems(
    pages: List<ScannedPage>,
    canEditPages: Boolean,
    onEvent: (PdfPageEditorUiEvent) -> Unit,
    onDeletePage: (String) -> Unit
) {
    itemsIndexed(
        items = pages,
        key = { _, page -> page.id }
    ) { index, page ->
        PdfPageRow(
            page = page,
            pageNumber = index + 1,
            isFirstPage = index == 0,
            isLastPage = index == pages.lastIndex,
            canEditPages = canEditPages,
            onEvent = onEvent,
            onDeletePage = onDeletePage
        )
    }
}

@Composable
private fun PdfPageRow(
    page: ScannedPage,
    pageNumber: Int,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    canEditPages: Boolean,
    onEvent: (PdfPageEditorUiEvent) -> Unit,
    onDeletePage: (String) -> Unit
) {
    DoclyAdaptiveLayout {
        if (it) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 152.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PdfPageThumbnail(page = page, pageNumber = pageNumber)
                PdfPageDetails(
                    page = page,
                    pageNumber = pageNumber,
                    isFirstPage = isFirstPage,
                    isLastPage = isLastPage,
                    canEditPages = canEditPages,
                    onEvent = onEvent,
                    onDeletePage = onDeletePage,
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
                PdfPageThumbnail(page = page, pageNumber = pageNumber)
                PdfPageDetails(
                    page = page,
                    pageNumber = pageNumber,
                    isFirstPage = isFirstPage,
                    isLastPage = isLastPage,
                    canEditPages = canEditPages,
                    onEvent = onEvent,
                    onDeletePage = onDeletePage,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PdfPageThumbnail(page: ScannedPage, pageNumber: Int) {
    DoclyImageThumbnail(
        imagePath = page.thumbnailPath,
        contentDescription = "Page $pageNumber thumbnail",
        modifier = Modifier
            .size(width = 84.dp, height = 112.dp)
            .graphicsLayer {
                rotationZ = page.rotationDegrees.toFloat()
            },
        testTag = DoclyTestTags.EDITOR_PAGE_THUMBNAIL,
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun PdfPageDetails(
    page: ScannedPage,
    pageNumber: Int,
    isFirstPage: Boolean,
    isLastPage: Boolean,
    canEditPages: Boolean,
    onEvent: (PdfPageEditorUiEvent) -> Unit,
    onDeletePage: (String) -> Unit,
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
                    onClick = { onEvent(PdfPageEditorUiEvent.OnMovePageUp(page.id)) },
                    enabled = canEditPages && !isFirstPage,
                    modifier = actionModifier.testTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_UP_ACTION)
                ) {
                    Text(text = "Up")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(PdfPageEditorUiEvent.OnMovePageDown(page.id)) },
                    enabled = canEditPages && !isLastPage,
                    modifier = actionModifier.testTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_DOWN_ACTION)
                ) {
                    Text(text = "Down")
                }
            }
        )
        DoclyAdaptiveTwoActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(PdfPageEditorUiEvent.OnRotatePageClicked(page.id)) },
                    enabled = canEditPages,
                    modifier = actionModifier.testTag(DoclyTestTags.PDF_PAGE_EDITOR_ROTATE_PAGE_ACTION)
                ) {
                    Text(text = "Rotate")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onDeletePage(page.id) },
                    enabled = canEditPages,
                    modifier = actionModifier.testTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_ACTION)
                ) {
                    Text(text = "Delete")
                }
            }
        )
    }
}

@Composable
private fun DeletePdfPageConfirmationDialog(
    pageNumber: Int?,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (pageNumber == null) return

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = { Text(text = "Delete page?") },
        text = { Text(text = "Delete page $pageNumber from this PDF?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                modifier = Modifier
                    .testTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_CONFIRM_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = if (isSaving) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier
                    .testTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_DISMISS_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier
            .testTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_DIALOG)
            .semantics {
                contentDescription = "Delete PDF page confirmation"
            }
    )
}

@Preview(showBackground = true)
@Composable
private fun PdfPageEditorScreenPreview() {
    DoclyTheme {
        PdfPageEditorScreen(
            uiState = PdfPageEditorUiState(
                documentId = "document-id",
                pages = listOf(
                    ScannedPage(
                        id = "page-id",
                        sessionId = "session-id",
                        pageIndex = 0,
                        originalImagePath = "/raw/page.jpg",
                        processedImagePath = "/processed/page.jpg",
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
