package com.docly.app.feature.pdfpageeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScannedPage
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPageEditorScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageToolControlsReflectBoundariesAndSaveAvailability() {
        renderEditor(
            uiState = PdfPageEditorUiState(
                documentId = "document-id",
                pages = listOf(samplePage(id = "first"), samplePage(id = "second", pageIndex = 1)),
                isLoading = false,
                isDirty = true
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.PDF_PAGE_EDITOR_SAVE_ACTION).assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_UP_ACTION)[0].assertIsNotEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_DOWN_ACTION)[0].assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_UP_ACTION)[1].assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_DOWN_ACTION)[1].assertIsNotEnabled()
    }

    @Test
    fun pageToolClicksDispatchEvents() {
        val receivedEvents = mutableListOf<PdfPageEditorUiEvent>()
        renderEditor(
            uiState = PdfPageEditorUiState(
                documentId = "document-id",
                pages = listOf(samplePage(id = "first"), samplePage(id = "second", pageIndex = 1)),
                isLoading = false,
                isDirty = true
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.PDF_PAGE_EDITOR_SAVE_ACTION).performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_MOVE_PAGE_DOWN_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_ROTATE_PAGE_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.PDF_PAGE_EDITOR_DELETE_PAGE_CONFIRM_ACTION).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PdfPageEditorUiEvent.OnLoad,
                    PdfPageEditorUiEvent.OnSaveClicked,
                    PdfPageEditorUiEvent.OnMovePageDown("first"),
                    PdfPageEditorUiEvent.OnRotatePageClicked("first"),
                    PdfPageEditorUiEvent.OnDeletePageClicked("first")
                ),
                receivedEvents
            )
        }
    }

    private fun renderEditor(uiState: PdfPageEditorUiState, onEvent: (PdfPageEditorUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DoclyTheme {
                PdfPageEditorScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun samplePage(id: String, pageIndex: Int = 0): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = "/processed/$id.jpg",
        thumbnailPath = "/thumb/$id.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )
}
