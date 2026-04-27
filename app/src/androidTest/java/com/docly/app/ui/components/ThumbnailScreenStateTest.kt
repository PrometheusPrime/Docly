package com.docly.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScannedPage
import com.docly.app.feature.editor.EditorScreen
import com.docly.app.feature.editor.EditorUiState
import com.docly.app.feature.library.LibraryScreen
import com.docly.app.feature.library.LibraryUiState
import com.docly.app.feature.review.ReviewScreen
import com.docly.app.feature.review.ReviewUiState
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reviewShowsLatestPageThumbnailSlot() {
        composeRule.setContent {
            DoclyTheme {
                ReviewScreen(
                    uiState = ReviewUiState(
                        sessionId = "session-id",
                        rawImagePath = "/raw/page.jpg",
                        thumbnailPath = "/thumb/page.jpg"
                    ),
                    onEvent = {},
                    onEditPages = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_PAGE_THUMBNAIL)
            .assertIsDisplayed()
    }

    @Test
    fun editorShowsPageThumbnailSlot() {
        composeRule.setContent {
            DoclyTheme {
                EditorScreen(
                    uiState = EditorUiState(
                        sessionId = "session-id",
                        pages = listOf(samplePage())
                    ),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_PAGE_THUMBNAIL)
            .assertIsDisplayed()
    }

    @Test
    fun libraryShowsDocumentThumbnailSlot() {
        composeRule.setContent {
            DoclyTheme {
                LibraryScreen(
                    uiState = LibraryUiState(documents = listOf(sampleDocument())),
                    onStartScanner = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DOCUMENT_THUMBNAIL)
            .assertIsDisplayed()
    }

    private fun samplePage(): ScannedPage = ScannedPage(
        id = "page-id",
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = "/raw/page.jpg",
        processedImagePath = null,
        thumbnailPath = "/thumb/page.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )

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
            paperType = "Past Paper"
        ),
        pageCount = 1,
        createdAt = 1L
    )
}
