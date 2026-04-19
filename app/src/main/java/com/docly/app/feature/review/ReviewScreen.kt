package com.docly.app.feature.review

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
fun ReviewScreen(
    sessionId: String,
    onEditPages: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Review",
        screenTestTag = DoclyTestTags.REVIEW_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Review pages",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Session ID: $sessionId",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onEditPages,
            modifier = Modifier.testTag(DoclyTestTags.REVIEW_EDITOR_ACTION)
        ) {
            Text(text = "Edit pages")
        }
        DoclyEmptyContent(
            title = "Page preview pending",
            message = "Accepted pages will be listed in order."
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    DoclyTheme {
        ReviewScreen(
            sessionId = PLACEHOLDER_SESSION_ID,
            onEditPages = {},
            onNavigateBack = {}
        )
    }
}
