package com.docly.app.feature.export

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ExportScreen(
    sessionId: String,
    onViewLibrary: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Export",
        screenTestTag = DoclyTestTags.EXPORT_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "PDF export",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Session ID: $sessionId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onViewLibrary,
            modifier = Modifier.testTag(DoclyTestTags.EXPORT_LIBRARY_ACTION)
        ) {
            Text(text = "View library")
        }
        DoclyLoadingContent(message = "Waiting for pages")
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    DoclyTheme {
        ExportScreen(
            sessionId = PLACEHOLDER_SESSION_ID,
            onViewLibrary = {},
            onNavigateBack = {}
        )
    }
}
