package com.docly.app.feature.documenteditor

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.docly.app.domain.model.DocumentType
import com.docly.app.ui.components.DoclyAdaptiveThreeActionRow
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun DocumentEditorScreen(
    uiState: DocumentEditorUiState,
    onEvent: (DocumentEditorUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEvent(DocumentEditorUiEvent.OnStart)
    }
    BackHandler(enabled = true) {
        onEvent(DocumentEditorUiEvent.OnNavigateBackClicked)
    }

    DoclyScreenScaffold(
        title = uiState.title,
        screenTestTag = DoclyTestTags.DOCUMENT_EDITOR_SCREEN,
        modifier = modifier,
        onNavigateBack = { onEvent(DocumentEditorUiEvent.OnNavigateBackClicked) },
        contentMaxWidth = 920.dp
    ) {
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Opening document...")

            uiState.errorMessage != null && !uiState.hasLoadedContent -> DoclyErrorContent(
                title = "Could not open document",
                message = uiState.errorMessage,
                actionLabel = "Retry",
                onAction = { onEvent(DocumentEditorUiEvent.OnRetryClicked) }
            )

            else -> DocumentEditorContent(uiState = uiState, onEvent = onEvent)
        }
    }

    UnsavedChangesDialog(
        isVisible = uiState.showUnsavedChangesDialog,
        onDiscard = { onEvent(DocumentEditorUiEvent.OnDiscardChangesConfirmed) },
        onDismiss = { onEvent(DocumentEditorUiEvent.OnUnsavedChangesDismissed) }
    )
}

@Composable
private fun DocumentEditorContent(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = uiState.documentType.editorLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (uiState.canPreview) {
            DocumentEditorModeSelector(uiState = uiState, onEvent = onEvent)
        }
        DocumentSearchControls(uiState = uiState, onEvent = onEvent)

        when (uiState.editorMode) {
            DocumentEditorMode.SOURCE -> DocumentSourceEditor(uiState = uiState, onEvent = onEvent)
            DocumentEditorMode.PREVIEW -> DocumentPreview(uiState = uiState)
        }

        Text(
            text = uiState.saveStatus.statusText(uiState),
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.saveStatus == DocumentEditorSaveStatus.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }

    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            Button(
                onClick = { onEvent(DocumentEditorUiEvent.OnSaveClicked) },
                enabled = uiState.canSave,
                modifier = actionModifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_SAVE_ACTION)
            ) {
                Icon(imageVector = Icons.Filled.Create, contentDescription = null)
                Text(text = if (uiState.isSaving) "Saving..." else "Save")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(DocumentEditorUiEvent.OnExportPdfClicked) },
                enabled = uiState.canExportPdf,
                modifier = actionModifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_EXPORT_PDF_ACTION)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = if (uiState.isExportingPdf) "Creating..." else "Create PDF")
            }
        }
    )

    uiState.errorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DocumentEditorModeSelector(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = uiState.editorMode == DocumentEditorMode.SOURCE,
            onClick = { onEvent(DocumentEditorUiEvent.OnEditorModeChanged(DocumentEditorMode.SOURCE)) },
            label = { Text(text = "Source") },
            modifier = Modifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_SOURCE_MODE_ACTION)
        )
        FilterChip(
            selected = uiState.editorMode == DocumentEditorMode.PREVIEW,
            onClick = { onEvent(DocumentEditorUiEvent.OnEditorModeChanged(DocumentEditorMode.PREVIEW)) },
            label = { Text(text = "Preview") },
            modifier = Modifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_PREVIEW_MODE_ACTION)
        )
    }
}

@Composable
private fun DocumentSearchControls(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { query -> onEvent(DocumentEditorUiEvent.OnSearchQueryChanged(query)) },
            label = { Text(text = "Find in document") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.DOCUMENT_EDITOR_SEARCH_FIELD)
        )
        DoclyAdaptiveThreeActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(DocumentEditorUiEvent.OnPreviousSearchResultClicked) },
                    enabled = uiState.hasSearchResults,
                    modifier = actionModifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_SEARCH_PREVIOUS_ACTION)
                ) {
                    Text(text = "Previous")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(DocumentEditorUiEvent.OnNextSearchResultClicked) },
                    enabled = uiState.hasSearchResults,
                    modifier = actionModifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_SEARCH_NEXT_ACTION)
                ) {
                    Text(text = "Next")
                }
            },
            third = { actionModifier ->
                Text(
                    text = uiState.searchSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = actionModifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_SEARCH_SUMMARY)
                )
            }
        )
    }
}

@Composable
private fun DocumentSourceEditor(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit) {
    OutlinedTextField(
        value = uiState.content,
        onValueChange = { content -> onEvent(DocumentEditorUiEvent.OnContentChanged(content)) },
        label = { Text(text = "Content") },
        enabled = uiState.canEdit,
        minLines = 14,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 420.dp)
            .testTag(DoclyTestTags.DOCUMENT_EDITOR_CONTENT_FIELD)
    )
}

@Composable
private fun DocumentPreview(uiState: DocumentEditorUiState) {
    if (uiState.isPreviewLoading) {
        DoclyLoadingContent(message = "Rendering preview...")
    } else {
        DocumentPreviewWebView(html = uiState.previewHtml)
    }
}

@Composable
private fun DocumentPreviewWebView(html: String) {
    var webView: WebView? = null
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                }
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 520.dp)
            .testTag(DoclyTestTags.DOCUMENT_EDITOR_PREVIEW_WEB_CONTENT)
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun UnsavedChangesDialog(isVisible: Boolean, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Discard unsaved changes?") },
        text = { Text(text = "Your latest changes have not been saved.") },
        confirmButton = {
            Button(
                onClick = onDiscard,
                modifier = Modifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_DISCARD_CHANGES_ACTION)
            ) {
                Text(text = "Discard")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_KEEP_EDITING_ACTION)
            ) {
                Text(text = "Keep editing")
            }
        },
        modifier = Modifier.testTag(DoclyTestTags.DOCUMENT_EDITOR_UNSAVED_DIALOG)
    )
}

private fun DocumentEditorSaveStatus.statusText(uiState: DocumentEditorUiState): String = when (this) {
    DocumentEditorSaveStatus.SAVED -> "Saved"
    DocumentEditorSaveStatus.UNSAVED -> "Unsaved changes"
    DocumentEditorSaveStatus.AUTOSAVING -> "Autosaving..."
    DocumentEditorSaveStatus.SAVING -> "Saving..."
    DocumentEditorSaveStatus.ERROR -> uiState.errorMessage ?: "Save failed"
}

private fun DocumentType?.editorLabel(): String = when (this) {
    DocumentType.TXT -> "TXT source"
    DocumentType.MARKDOWN -> "Markdown source"
    DocumentType.HTML -> "HTML source"
    else -> "Source"
}

@Preview(showBackground = true)
@Composable
private fun DocumentEditorScreenPreview() {
    DoclyTheme {
        DocumentEditorScreen(
            uiState = DocumentEditorUiState(
                documentId = "document-id",
                title = "Notes",
                documentType = DocumentType.MARKDOWN,
                content = "# Notes\n\n",
                isLoading = false,
                hasLoadedContent = true
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
