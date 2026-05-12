package com.docly.app.feature.documenteditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.DocumentType
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentEditorScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun markdownEditorShowsSourcePreviewSearchAndDispatchesEvents() {
        val receivedEvents = mutableListOf<DocumentEditorUiEvent>()
        renderEditor(
            uiState = loadedMarkdownState(),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_CONTENT_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_SEARCH_FIELD).performTextInput("Heading")
        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_PREVIEW_MODE_ACTION).performClick()
        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_SAVE_ACTION).performScrollTo().performClick()
        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_EXPORT_PDF_ACTION).performScrollTo().performClick()

        composeRule.runOnIdle {
            val userEvents = receivedEvents.filterNot { event ->
                event == DocumentEditorUiEvent.OnSearchQueryChanged("")
            }
            assertEquals(
                listOf(
                    DocumentEditorUiEvent.OnStart,
                    DocumentEditorUiEvent.OnSearchQueryChanged("Heading"),
                    DocumentEditorUiEvent.OnEditorModeChanged(DocumentEditorMode.PREVIEW),
                    DocumentEditorUiEvent.OnSaveClicked,
                    DocumentEditorUiEvent.OnExportPdfClicked
                ),
                userEvents
            )
        }
    }

    @Test
    fun previewModeShowsSecuredPreviewContainer() {
        renderEditor(
            uiState = loadedMarkdownState().copy(
                editorMode = DocumentEditorMode.PREVIEW,
                previewHtml = "<html><body><h1>Heading</h1></body></html>"
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_PREVIEW_WEB_CONTENT).assertIsDisplayed()
    }

    @Test
    fun unsavedChangesDialogShowsDiscardAndKeepEditingActions() {
        renderEditor(uiState = loadedMarkdownState().copy(showUnsavedChangesDialog = true))

        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_UNSAVED_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Discard").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").assertIsDisplayed()
    }

    private fun renderEditor(uiState: DocumentEditorUiState, onEvent: (DocumentEditorUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DoclyTheme {
                DocumentEditorScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun loadedMarkdownState(): DocumentEditorUiState = DocumentEditorUiState(
        documentId = "document-id",
        title = "Notes",
        documentType = DocumentType.MARKDOWN,
        content = "# Heading",
        isLoading = false,
        hasLoadedContent = true,
        isDirty = true,
        saveStatus = DocumentEditorSaveStatus.UNSAVED
    )
}
