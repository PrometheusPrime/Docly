package com.docly.app.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.DocumentType
import com.docly.app.ui.components.DoclyAdaptiveThreeActionRow
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun CreateScreen(
    uiState: CreateUiState,
    onEvent: (CreateUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Create",
        screenTestTag = DoclyTestTags.CREATE_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        OutlinedTextField(
            value = uiState.title,
            onValueChange = { title -> onEvent(CreateUiEvent.OnTitleChanged(title)) },
            label = { Text(text = "Title") },
            singleLine = true,
            enabled = !uiState.isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CREATE_TITLE_FIELD)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Type",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            DoclyAdaptiveThreeActionRow(
                first = { actionModifier ->
                    DocumentTypeChip(
                        label = "TXT",
                        selected = uiState.selectedType == DocumentType.TXT,
                        enabled = !uiState.isCreating,
                        onClick = { onEvent(CreateUiEvent.OnTypeSelected(DocumentType.TXT)) },
                        modifier = actionModifier.testTag(DoclyTestTags.CREATE_TYPE_TXT_OPTION)
                    )
                },
                second = { actionModifier ->
                    DocumentTypeChip(
                        label = "Markdown",
                        selected = uiState.selectedType == DocumentType.MARKDOWN,
                        enabled = !uiState.isCreating,
                        onClick = { onEvent(CreateUiEvent.OnTypeSelected(DocumentType.MARKDOWN)) },
                        modifier = actionModifier.testTag(DoclyTestTags.CREATE_TYPE_MARKDOWN_OPTION)
                    )
                },
                third = { actionModifier ->
                    DocumentTypeChip(
                        label = "HTML",
                        selected = uiState.selectedType == DocumentType.HTML,
                        enabled = !uiState.isCreating,
                        onClick = { onEvent(CreateUiEvent.OnTypeSelected(DocumentType.HTML)) },
                        modifier = actionModifier.testTag(DoclyTestTags.CREATE_TYPE_HTML_OPTION)
                    )
                }
            )
        }

        Button(
            onClick = { onEvent(CreateUiEvent.OnCreateClicked) },
            enabled = uiState.canCreate,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CREATE_DOCUMENT_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Icon(imageVector = Icons.Filled.Create, contentDescription = null)
            Text(text = if (uiState.isCreating) "Creating..." else "Create document")
        }

        OutlinedButton(
            onClick = { onEvent(CreateUiEvent.OnCreatePdfFromScanClicked) },
            enabled = !uiState.isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CREATE_SCAN_PDF_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Text(text = "PDF from scan/images")
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun DocumentTypeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun CreateScreenPreview() {
    DoclyTheme {
        CreateScreen(
            uiState = CreateUiState(),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
