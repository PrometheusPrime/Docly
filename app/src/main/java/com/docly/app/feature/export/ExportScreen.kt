package com.docly.app.feature.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ExportScreen(
    uiState: ExportUiState,
    onEvent: (ExportUiEvent) -> Unit,
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
            text = "Export PDF",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Preparing export...")
            else -> ExportContent(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun ExportContent(uiState: ExportUiState, onEvent: (ExportUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.EXPORT_SUMMARY),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Session ID: ${uiState.sessionId}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SummaryLine(
            label = "File name",
            value = uiState.fileName.ifBlank { "Not ready" },
            modifier = Modifier.testTag(DoclyTestTags.EXPORT_FILENAME)
        )
        SummaryLine(label = "Details", value = uiState.metadataSummary.ifBlank { "Document details required" })
        SummaryLine(label = "Pages", value = uiState.pageCount.toString())

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(DoclyTestTags.EXPORT_ERROR_MESSAGE)
            )
        }

        ExportPrimaryAction(uiState = uiState, onEvent = onEvent)

        if (uiState.hasExportedPdf) {
            ExportCompletedActions(onEvent = onEvent)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ExportPrimaryAction(uiState: ExportUiState, onEvent: (ExportUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(ExportUiEvent.OnExportClicked) },
        enabled = uiState.canExport,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.EXPORT_PDF_ACTION)
    ) {
        Text(text = if (uiState.isExporting) "Exporting..." else "Export PDF")
    }
}

@Composable
private fun ExportCompletedActions(onEvent: (ExportUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onEvent(ExportUiEvent.OnOpenPdfClicked) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.EXPORT_OPEN_ACTION)
            ) {
                Text(text = "Open")
            }
            OutlinedButton(
                onClick = { onEvent(ExportUiEvent.OnSharePdfClicked) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.EXPORT_SHARE_ACTION)
            ) {
                Text(text = "Share")
            }
        }
        Button(
            onClick = { onEvent(ExportUiEvent.OnOpenLibraryClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.EXPORT_LIBRARY_ACTION)
        ) {
            Text(text = "View library")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExportScreenPreview() {
    DoclyTheme {
        ExportScreen(
            uiState = ExportUiState(
                sessionId = PLACEHOLDER_SESSION_ID,
                fileName = "grade_10_math_2026_past_paper.pdf",
                title = "grade_10_math_2026_past_paper",
                metadataSummary = "Grade 10 - Math - 2026 - Past Paper",
                pageCount = 2,
                isExportReady = true
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
