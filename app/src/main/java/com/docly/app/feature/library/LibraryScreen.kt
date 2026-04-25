package com.docly.app.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

@Composable
fun LibraryScreen(uiState: LibraryUiState, onStartScanner: () -> Unit, modifier: Modifier = Modifier) {
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
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading documents...")

            uiState.errorMessage != null -> DoclyErrorContent(
                title = "Could not load library",
                message = uiState.errorMessage
            )

            uiState.documents.isEmpty() -> DoclyEmptyContent(
                title = "No saved documents",
                message = "Exported PDFs will appear here.",
                actionLabel = "Start scan",
                onAction = onStartScanner
            )

            else -> LibraryDocumentList(documents = uiState.documents)
        }
    }
}

@Composable
private fun LibraryDocumentList(documents: List<SavedDocument>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        documents.forEach { document ->
            LibraryDocumentRow(document = document)
        }
    }
}

@Composable
private fun LibraryDocumentRow(document: SavedDocument) {
    val pageCountText = "${document.pageCount} page${if (document.pageCount == 1) "" else "s"}"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DoclyImageThumbnail(
            imagePath = document.thumbnailPath,
            contentDescription = "${document.title} thumbnail",
            modifier = Modifier.size(width = 72.dp, height = 96.dp),
            testTag = DoclyTestTags.LIBRARY_DOCUMENT_THUMBNAIL
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${document.metadata.subject} - $pageCountText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    DoclyTheme {
        LibraryScreen(
            uiState = LibraryUiState(
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
            onStartScanner = {}
        )
    }
}
