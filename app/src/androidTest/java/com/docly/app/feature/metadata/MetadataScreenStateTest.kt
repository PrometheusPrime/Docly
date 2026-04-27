package com.docly.app.feature.metadata

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metadataFieldsRenderAndDispatchEvents() {
        val receivedEvents = mutableListOf<MetadataUiEvent>()
        renderStatefulMetadata(onEvent = { event -> receivedEvents += event })

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_GRADE_FIELD)
            .performTextInput("Grade 10")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_SUBJECT_FIELD)
            .performTextInput("Mathematics")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_YEAR_FIELD)
            .performTextInput("2026")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_PAPER_TYPE_FIELD)
            .performScrollTo()
            .performTextInput("Past Paper")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_PAPER_NUMBER_FIELD)
            .performScrollTo()
            .performTextInput("1")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_SOURCE_FIELD)
            .performScrollTo()
            .performTextInput("Archive")
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_NOTES_FIELD)
            .performScrollTo()
            .performTextInput("Clean copy")

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    MetadataUiEvent.OnGradeChanged("Grade 10"),
                    MetadataUiEvent.OnSubjectChanged("Mathematics"),
                    MetadataUiEvent.OnYearChanged("2026"),
                    MetadataUiEvent.OnPaperTypeChanged("Past Paper"),
                    MetadataUiEvent.OnPaperNumberChanged("1"),
                    MetadataUiEvent.OnSourceChanged("Archive"),
                    MetadataUiEvent.OnNotesChanged("Clean copy")
                ),
                receivedEvents
            )
        }
    }

    @Test
    fun filenamePreviewAndValidationErrorsAreDisplayed() {
        renderMetadata(
            uiState = MetadataUiState(
                sessionId = SESSION_ID,
                generatedFileName = "grade_10_mathematics_2026_past_paper.pdf",
                validationErrors = listOf("Subject is required.", "Year must be between 1980 and 2027.")
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_VALIDATION_ERRORS)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Subject is required.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Year must be between 1980 and 2027.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.METADATA_FILENAME_PREVIEW)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("grade_10_mathematics_2026_past_paper.pdf")
            .assertIsDisplayed()
    }

    @Test
    fun continueClickDispatchesEvent() {
        val receivedEvents = mutableListOf<MetadataUiEvent>()
        renderMetadata(onEvent = { event -> receivedEvents += event })

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_CONTINUE_ACTION)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(MetadataUiEvent.OnContinueClicked), receivedEvents)
        }
    }

    @Test
    fun continueIsDisabledWhileLoading() {
        renderMetadata(uiState = MetadataUiState(sessionId = SESSION_ID, isLoading = true))

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_CONTINUE_ACTION)
            .assertIsNotEnabled()
    }

    @Test
    fun continueIsDisabledWhileSaving() {
        renderMetadata(uiState = MetadataUiState(sessionId = SESSION_ID, isSaving = true))

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_CONTINUE_ACTION)
            .assertIsNotEnabled()
    }

    private fun renderMetadata(
        uiState: MetadataUiState = MetadataUiState(sessionId = SESSION_ID),
        onEvent: (MetadataUiEvent) -> Unit = {}
    ) {
        composeRule.setContent {
            DoclyTheme {
                MetadataScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun renderStatefulMetadata(
        initialUiState: MetadataUiState = MetadataUiState(sessionId = SESSION_ID),
        onEvent: (MetadataUiEvent) -> Unit = {}
    ) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(initialUiState) }
            DoclyTheme {
                MetadataScreen(
                    uiState = uiState,
                    onEvent = { event ->
                        uiState = uiState.withEvent(event)
                        onEvent(event)
                    },
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun MetadataUiState.withEvent(event: MetadataUiEvent): MetadataUiState = when (event) {
        MetadataUiEvent.OnContinueClicked -> this
        MetadataUiEvent.OnLoad -> this
        is MetadataUiEvent.OnGradeChanged -> copy(grade = event.value)
        is MetadataUiEvent.OnNotesChanged -> copy(notes = event.value)
        is MetadataUiEvent.OnPaperNumberChanged -> copy(paperNumber = event.value)
        is MetadataUiEvent.OnPaperTypeChanged -> copy(paperType = event.value)
        is MetadataUiEvent.OnSourceChanged -> copy(source = event.value)
        is MetadataUiEvent.OnSubjectChanged -> copy(subject = event.value)
        is MetadataUiEvent.OnYearChanged -> copy(year = event.value)
    }

    private companion object {
        const val SESSION_ID = "session-id"
    }
}
