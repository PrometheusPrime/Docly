package com.docly.app.feature.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.app.navigation.PLACEHOLDER_SESSION_ID
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun MetadataScreen(
    uiState: MetadataUiState,
    onEvent: (MetadataUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            text = "Session ID: ${uiState.sessionId}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MetadataContinueAction(uiState = uiState, onEvent = onEvent)

        when {
            uiState.isLoading -> DoclyLoadingContent(message = "Loading metadata...")
            else -> MetadataForm(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun MetadataContinueAction(uiState: MetadataUiState, onEvent: (MetadataUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(MetadataUiEvent.OnContinueClicked) },
        enabled = uiState.canContinue,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.METADATA_CONTINUE_ACTION)
    ) {
        Text(text = if (uiState.isSaving) "Saving..." else "Continue to export")
    }
}

@Composable
private fun MetadataForm(uiState: MetadataUiState, onEvent: (MetadataUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(DoclyTestTags.METADATA_ERROR_MESSAGE)
            )
        }

        if (uiState.validationErrors.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.METADATA_VALIDATION_ERRORS),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.validationErrors.forEach { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        MetadataTextField(
            label = "Grade",
            value = uiState.grade,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnGradeChanged(value)) },
            testTag = DoclyTestTags.METADATA_GRADE_FIELD,
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Subject",
            value = uiState.subject,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnSubjectChanged(value)) },
            testTag = DoclyTestTags.METADATA_SUBJECT_FIELD,
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Year",
            value = uiState.year,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnYearChanged(value)) },
            testTag = DoclyTestTags.METADATA_YEAR_FIELD,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Paper type",
            value = uiState.paperType,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnPaperTypeChanged(value)) },
            testTag = DoclyTestTags.METADATA_PAPER_TYPE_FIELD,
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Paper number",
            value = uiState.paperNumber,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnPaperNumberChanged(value)) },
            testTag = DoclyTestTags.METADATA_PAPER_NUMBER_FIELD,
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Source",
            value = uiState.source,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnSourceChanged(value)) },
            testTag = DoclyTestTags.METADATA_SOURCE_FIELD,
            enabled = !uiState.isSaving
        )
        MetadataTextField(
            label = "Notes",
            value = uiState.notes,
            onValueChange = { value -> onEvent(MetadataUiEvent.OnNotesChanged(value)) },
            testTag = DoclyTestTags.METADATA_NOTES_FIELD,
            singleLine = false,
            minLines = 3,
            enabled = !uiState.isSaving
        )
        FileNamePreview(generatedFileName = uiState.generatedFileName)
    }
}

@Composable
private fun MetadataTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun FileNamePreview(generatedFileName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.METADATA_FILENAME_PREVIEW),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "File name preview",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = generatedFileName.ifBlank { "Complete required fields to preview the PDF file name." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MetadataScreenPreview() {
    DoclyTheme {
        MetadataScreen(
            uiState = MetadataUiState(
                sessionId = PLACEHOLDER_SESSION_ID,
                grade = "Grade 10",
                subject = "Mathematics",
                year = "2026",
                paperType = "Past Paper",
                generatedFileName = "grade_10_mathematics_2026_past_paper.pdf"
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
