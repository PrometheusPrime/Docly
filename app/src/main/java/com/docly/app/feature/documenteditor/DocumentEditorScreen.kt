package com.docly.app.feature.documenteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.DocumentType
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

    DoclyScreenScaffold(
        title = uiState.title,
        screenTestTag = DoclyTestTags.DOCUMENT_EDITOR_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
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
}

@Composable
private fun DocumentEditorContent(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = uiState.documentType.editorLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Text(
            text = if (uiState.isDirty) "Unsaved changes" else "Saved",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
