package com.docly.app.feature.metadata

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
fun MetadataScreen(sessionId: String, onExport: () -> Unit, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    DoclyScreenScaffold(
        title = "Metadata",
        screenTestTag = DoclyTestTags.METADATA_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Document details",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Session ID: $sessionId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onExport,
            modifier = Modifier.testTag(DoclyTestTags.METADATA_EXPORT_ACTION)
        ) {
            Text(text = "Export PDF")
        }
        DoclyEmptyContent(
            title = "No metadata entered",
            message = "Grade, subject, year, and paper type fields will appear here."
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataScreenPreview() {
    DoclyTheme {
        MetadataScreen(
            sessionId = PLACEHOLDER_SESSION_ID,
            onExport = {},
            onNavigateBack = {}
        )
    }
}
