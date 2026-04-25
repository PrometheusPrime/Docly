package com.docly.app.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onEditMetadata: () -> Unit,
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
        Button(
            onClick = onEditMetadata,
            modifier = Modifier.testTag(DoclyTestTags.EDITOR_METADATA_ACTION)
        ) {
            Text(text = "Add metadata")
        }
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading pages...")

            uiState.errorMessage != null -> DoclyErrorContent(
                title = "Could not load pages",
                message = uiState.errorMessage
            )

            uiState.pages.isEmpty() -> DoclyEmptyContent(
                title = "No editable pages",
                message = "Page ordering, rotation, and deletion controls will appear here."
            )

            else -> EditorPageList(pages = uiState.pages)
        }
    }
}

@Composable
private fun EditorPageList(pages: List<ScannedPage>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pages.forEachIndexed { index, page ->
            EditorPageRow(page = page, pageNumber = index + 1)
        }
    }
}

@Composable
private fun EditorPageRow(page: ScannedPage, pageNumber: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DoclyImageThumbnail(
            imagePath = page.thumbnailPath ?: page.processedImagePath ?: page.originalImagePath,
            contentDescription = "Page $pageNumber thumbnail",
            modifier = Modifier.size(width = 84.dp, height = 112.dp),
            testTag = DoclyTestTags.EDITOR_PAGE_THUMBNAIL
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            onEditMetadata = {},
            onNavigateBack = {}
        )
    }
}
