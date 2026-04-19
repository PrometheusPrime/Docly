package com.docly.app.feature.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun LibraryScreen(onStartScanner: () -> Unit, modifier: Modifier = Modifier) {
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
        DoclyEmptyContent(
            title = "No saved documents",
            message = "Exported PDFs will appear here.",
            actionLabel = "Start scan",
            onAction = onStartScanner
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    DoclyTheme {
        LibraryScreen(onStartScanner = {})
    }
}
