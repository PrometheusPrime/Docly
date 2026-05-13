package com.docly.app.feature.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ConverterScreen(
    uiState: ConverterUiState,
    onEvent: (ConverterUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEvent(ConverterUiEvent.OnStart)
    }

    DoclyScreenScaffold(
        title = "Convert",
        screenTestTag = DoclyTestTags.CONVERTER_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = "Document converter",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading documents...")

            !uiState.hasConvertibleDocuments -> DoclyEmptyContent(
                title = "No convertible documents",
                message = "Import or create a supported document before converting."
            )

            else -> ConverterContent(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun ConverterContent(uiState: ConverterUiState, onEvent: (ConverterUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        InputSelection(uiState = uiState, onEvent = onEvent)
        OutputSelection(uiState = uiState, onEvent = onEvent)

        if (uiState.xlsxSheets.isNotEmpty()) {
            SheetSelection(uiState = uiState, onEvent = onEvent)
        }

        OutlinedTextField(
            value = uiState.outputFileName,
            onValueChange = { value -> onEvent(ConverterUiEvent.OnOutputFileNameChanged(value)) },
            label = { Text(text = "Output file name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CONVERTER_OUTPUT_NAME_FIELD)
        )

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(DoclyTestTags.CONVERTER_ERROR_MESSAGE)
            )
        }

        if (uiState.isConverting) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.CONVERTER_PROGRESS)
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "Converting... ${uiState.progress}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = { onEvent(ConverterUiEvent.OnConvertClicked) },
            enabled = uiState.canConvert,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CONVERTER_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = if (uiState.isConverting) "Converting..." else "Convert")
        }

        if (uiState.hasCompletedOutput) {
            CompletedActions(onEvent = onEvent)
        }
    }
}

@Composable
private fun InputSelection(uiState: ConverterUiState, onEvent: (ConverterUiEvent) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.CONVERTER_INPUT_LIST)
    ) {
        Text(
            text = "Input",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        uiState.documents.forEach { document ->
            val selected = document.id == uiState.selectedDocumentId
            val onClick = { onEvent(ConverterUiEvent.OnInputSelected(document.id)) }
            if (selected) {
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DoclyTestTags.CONVERTER_INPUT_OPTION)
                        .doclyMinimumTouchTarget()
                ) {
                    DocumentOptionText(document = document)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DoclyTestTags.CONVERTER_INPUT_OPTION)
                        .doclyMinimumTouchTarget()
                ) {
                    DocumentOptionText(document = document)
                }
            }
        }
    }
}

@Composable
private fun DocumentOptionText(document: DoclyDocument) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = document.name, style = MaterialTheme.typography.labelLarge)
        Text(
            text = "${document.type.label} - ${document.fileSize.toReadableSize()}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun OutputSelection(uiState: ConverterUiState, onEvent: (ConverterUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Output",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.supportedOutputTypes.forEach { outputType ->
                FilterChip(
                    selected = outputType == uiState.selectedOutputType,
                    onClick = { onEvent(ConverterUiEvent.OnOutputTypeSelected(outputType)) },
                    label = { Text(text = outputType.label) },
                    modifier = Modifier.testTag(DoclyTestTags.CONVERTER_OUTPUT_OPTION)
                )
            }
        }
    }
}

@Composable
private fun SheetSelection(uiState: ConverterUiState, onEvent: (ConverterUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (uiState.isLoadingSheets) "Loading sheets..." else "Sheet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            uiState.xlsxSheets.forEach { sheet ->
                FilterChip(
                    selected = sheet.index == uiState.selectedXlsxSheetIndex,
                    onClick = { onEvent(ConverterUiEvent.OnXlsxSheetSelected(sheet.index)) },
                    label = { Text(text = sheet.name) },
                    modifier = Modifier.testTag(DoclyTestTags.CONVERTER_SHEET_OPTION)
                )
            }
        }
    }
}

@Composable
private fun CompletedActions(onEvent: (ConverterUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        DoclyAdaptiveTwoActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ConverterUiEvent.OnOpenResultClicked) },
                    modifier = actionModifier.testTag(DoclyTestTags.CONVERTER_OPEN_ACTION)
                ) {
                    Text(text = "Open")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ConverterUiEvent.OnShareResultClicked) },
                    modifier = actionModifier.testTag(DoclyTestTags.CONVERTER_SHARE_ACTION)
                ) {
                    Text(text = "Share")
                }
            }
        )
        Button(
            onClick = { onEvent(ConverterUiEvent.OnViewDocumentsClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CONVERTER_LIBRARY_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = "View Documents")
        }
    }
}

private val DocumentType.label: String
    get() = when (this) {
        DocumentType.PDF -> "PDF"
        DocumentType.TXT -> "TXT"
        DocumentType.MARKDOWN -> "Markdown"
        DocumentType.HTML -> "HTML"
        DocumentType.DOCX -> "DOCX"
        DocumentType.XLSX -> "XLSX"
        DocumentType.CSV -> "CSV"
        DocumentType.IMAGE -> "Image"
        DocumentType.UNKNOWN -> "Unknown"
    }

private fun Long.toReadableSize(): String = when {
    this <= 0L -> "Unknown size"
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${this / (1024L * 1024L)} MB"
}

@Preview(showBackground = true)
@Composable
private fun ConverterScreenPreview() {
    DoclyTheme {
        ConverterScreen(
            uiState = ConverterUiState(
                documents = listOf(
                    DoclyDocument(
                        id = "document-id",
                        name = "Notes",
                        type = DocumentType.MARKDOWN,
                        mimeType = "text/markdown",
                        fileRef = FileRef.InternalFile("/documents/notes.md"),
                        source = DocumentSource.CREATED,
                        fileSize = 1200L,
                        createdAt = 1L,
                        updatedAt = 1L
                    )
                ),
                selectedDocumentId = "document-id",
                selectedOutputType = DocumentType.PDF,
                supportedOutputTypes = listOf(DocumentType.PDF, DocumentType.HTML, DocumentType.TXT),
                outputFileName = "Notes.pdf",
                xlsxSheets = listOf(XlsxSheetInfo(name = "Sheet 1", index = 0)),
                isLoading = false
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
