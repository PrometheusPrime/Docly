package com.docly.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onEvent: (LibraryUiEvent) -> Unit,
    onStartScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Library",
        screenTestTag = DoclyTestTags.LIBRARY_SCREEN,
        modifier = modifier,
        actions = {
            IconButton(onClick = onStartScanner) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New scan"
                )
            }
        }
    ) {
        Text(
            text = "Saved documents",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Button(
            onClick = onStartScanner,
            modifier = Modifier.testTag(DoclyTestTags.LIBRARY_SCANNER_ACTION)
        ) {
            Text(text = "New scan")
        }
        if (uiState.hasDocuments || uiState.hasActiveSearch) {
            LibrarySearch(
                query = uiState.searchQuery,
                onEvent = onEvent
            )
        }
        val showBlockingError = uiState.errorMessage != null && !uiState.hasDocuments
        if (uiState.errorMessage != null && !showBlockingError) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading documents...")

            showBlockingError -> DoclyErrorContent(
                title = "Could not load library",
                message = checkNotNull(uiState.errorMessage)
            )

            !uiState.hasDocuments -> DoclyEmptyContent(
                title = "No saved documents",
                message = "Exported PDFs will appear here.",
                actionLabel = "Start scan",
                onAction = onStartScanner
            )

            uiState.documents.isEmpty() -> DoclyEmptyContent(
                title = "No matching documents",
                message = "Try a different search.",
                actionLabel = "Clear search",
                onAction = { onEvent(LibraryUiEvent.OnClearSearchClicked) }
            )

            else -> LibraryDocumentList(documents = uiState.documents, onEvent = onEvent)
        }
    }

    DeleteConfirmationDialog(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun LibrarySearch(query: String, onEvent: (LibraryUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value -> onEvent(LibraryUiEvent.OnSearchQueryChanged(value)) },
            label = { Text(text = "Search library") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.LIBRARY_SEARCH_FIELD)
        )
        if (query.isNotBlank()) {
            OutlinedButton(
                onClick = { onEvent(LibraryUiEvent.OnClearSearchClicked) },
                modifier = Modifier.testTag(DoclyTestTags.LIBRARY_CLEAR_SEARCH_ACTION)
            ) {
                Text(text = "Clear search")
            }
        }
    }
}

@Composable
private fun LibraryDocumentList(documents: List<SavedDocument>, onEvent: (LibraryUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        documents.forEach { document ->
            LibraryDocumentRow(document = document, onEvent = onEvent)
        }
    }
}

@Composable
private fun LibraryDocumentRow(document: SavedDocument, onEvent: (LibraryUiEvent) -> Unit) {
    val pageCountText = "${document.pageCount} page${if (document.pageCount == 1) "" else "s"}"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.LIBRARY_DOCUMENT_ROW)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DoclyImageThumbnail(
                imagePath = document.thumbnailPath,
                contentDescription = "${document.title} thumbnail",
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                testTag = DoclyTestTags.LIBRARY_DOCUMENT_THUMBNAIL
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = document.metadata.summaryText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$pageCountText - Created ${document.createdAt.formattedDate()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onEvent(LibraryUiEvent.OnOpenDocumentClicked(document.id)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.LIBRARY_OPEN_ACTION)
            ) {
                Text(text = "Open")
            }
            OutlinedButton(
                onClick = { onEvent(LibraryUiEvent.OnShareDocumentClicked(document.id)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.LIBRARY_SHARE_ACTION)
            ) {
                Text(text = "Share")
            }
            OutlinedButton(
                onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.LIBRARY_DELETE_ACTION)
            ) {
                Text(text = "Delete")
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(uiState: LibraryUiState, onEvent: (LibraryUiEvent) -> Unit) {
    val document = uiState.pendingDeleteDocument ?: return

    AlertDialog(
        onDismissRequest = {
            if (!uiState.isDeleting) {
                onEvent(LibraryUiEvent.OnDeleteDocumentDismissed)
            }
        },
        title = { Text(text = "Delete document?") },
        text = {
            Text(text = "Delete \"${document.title}\" from the library? This also removes its PDF and thumbnail.")
        },
        confirmButton = {
            Button(
                onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentConfirmed) },
                enabled = !uiState.isDeleting,
                modifier = Modifier.testTag(DoclyTestTags.LIBRARY_DELETE_CONFIRM_ACTION)
            ) {
                Text(text = if (uiState.isDeleting) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentDismissed) },
                enabled = !uiState.isDeleting,
                modifier = Modifier.testTag(DoclyTestTags.LIBRARY_DELETE_DISMISS_ACTION)
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.testTag(DoclyTestTags.LIBRARY_DELETE_DIALOG)
    )
}

private fun DocumentMetadata.summaryText(): String {
    val paperNumberText = paperNumber?.takeIf { it.isNotBlank() }?.let { paperNumber -> " $paperNumber" }.orEmpty()
    return "$grade - $subject - $year - $paperType$paperNumberText"
}

private fun Long.formattedDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    DoclyTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                totalDocumentCount = 1,
                documents = listOf(
                    SavedDocument(
                        id = "document-id",
                        sessionId = "session-id",
                        title = "Math Paper",
                        pdfPath = "/pdf/math.pdf",
                        thumbnailPath = "/thumb/math.jpg",
                        metadata = DocumentMetadata(
                            grade = "Grade 10",
                            subject = "Math",
                            year = 2026,
                            paperType = "Past Paper"
                        ),
                        pageCount = 2,
                        createdAt = 1L
                    )
                )
            ),
            onEvent = {},
            onStartScanner = {}
        )
    }
}
