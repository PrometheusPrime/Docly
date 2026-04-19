package com.docly.app.feature.editor

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun EditorScreen(
    sessionId: String,
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
            text = "Session ID: $sessionId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onEditMetadata,
            modifier = Modifier.testTag(DoclyTestTags.EDITOR_METADATA_ACTION)
        ) {
            Text(text = "Add metadata")
        }
        DoclyEmptyContent(
            title = "No editable pages",
            message = "Page ordering, rotation, and deletion controls will appear here."
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EditorScreenPreview() {
    DoclyTheme {
        EditorScreen(
            sessionId = PLACEHOLDER_SESSION_ID,
            onEditMetadata = {},
            onNavigateBack = {}
        )
    }
}
