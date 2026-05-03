package com.docly.app.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsStartScanAction() {
        renderLibrary(uiState = LibraryUiState())

        composeRule.onNodeWithTag(DoclyTestTags.EMPTY_CONTENT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("No saved documents")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCANNER_ACTION)
            .assertIsDisplayed()
    }

    @Test
    fun populatedStateShowsDocumentDetailsAndActions() {
        val document = sampleDocument()
        val receivedEvents = mutableListOf<LibraryUiEvent>()
        renderLibrary(
            uiState = LibraryUiState(
                documents = listOf(document),
                totalDocumentCount = 1
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SEARCH_FIELD)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DOCUMENT_ROW)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DOCUMENT_THUMBNAIL)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Math Paper")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Grade 10 - Math - 2026 - Past Paper 1")
            .assertIsDisplayed()
        composeRule.onNodeWithText("2 pages", substring = true)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_OPEN_ACTION)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SHARE_ACTION)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DELETE_ACTION)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    LibraryUiEvent.OnOpenDocumentClicked(document.id),
                    LibraryUiEvent.OnShareDocumentClicked(document.id),
                    LibraryUiEvent.OnDeleteDocumentClicked(document.id)
                ),
                receivedEvents
            )
        }
    }

    @Test
    fun searchFieldDispatchesQuery() {
        val receivedEvents = mutableListOf<LibraryUiEvent>()
        renderLibrary(
            uiState = LibraryUiState(
                documents = listOf(sampleDocument()),
                totalDocumentCount = 1
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SEARCH_FIELD)
            .performTextInput("science")

        composeRule.runOnIdle {
            assertEquals(LibraryUiEvent.OnSearchQueryChanged("science"), receivedEvents.last())
        }
    }

    @Test
    fun noResultsStateCanClearSearch() {
        val receivedEvents = mutableListOf<LibraryUiEvent>()
        renderLibrary(
            uiState = LibraryUiState(
                documents = emptyList(),
                totalDocumentCount = 1,
                searchQuery = "science"
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithText("No matching documents")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_CLEAR_SEARCH_ACTION)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(LibraryUiEvent.OnClearSearchClicked), receivedEvents)
        }
    }

    @Test
    fun deleteDialogDispatchesDismissAndConfirmEvents() {
        val document = sampleDocument()
        val receivedEvents = mutableListOf<LibraryUiEvent>()
        renderLibrary(
            uiState = LibraryUiState(
                documents = listOf(document),
                totalDocumentCount = 1,
                pendingDeleteDocument = document
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DELETE_DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Delete document?")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DELETE_DISMISS_ACTION)
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DELETE_CONFIRM_ACTION)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    LibraryUiEvent.OnDeleteDocumentDismissed,
                    LibraryUiEvent.OnDeleteDocumentConfirmed
                ),
                receivedEvents
            )
        }
    }

    private fun renderLibrary(
        uiState: LibraryUiState,
        onEvent: (LibraryUiEvent) -> Unit = {},
        onStartScanner: () -> Unit = {}
    ) {
        composeRule.setContent {
            DoclyTheme {
                LibraryScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onStartScanner = onStartScanner
                )
            }
        }
    }

    private fun sampleDocument(): SavedDocument = SavedDocument(
        id = "document-id",
        sessionId = "session-id",
        title = "Math Paper",
        pdfPath = "/pdf/math.pdf",
        thumbnailPath = "/thumb/math.jpg",
        metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1"
        ),
        pageCount = 2,
        createdAt = 1L
    )
}
