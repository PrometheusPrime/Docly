package com.docly.app.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Rule
import org.junit.Test

class LibraryScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyDocumentsShowsImportAndScanActions() {
        composeRule.setContent {
            DoclyTheme {
                LibraryScreen(
                    uiState = LibraryUiState(),
                    onEvent = {},
                    onStartScanner = {}
                )
            }
        }

        composeRule.onNodeWithText("No documents").assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_IMPORT_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCANNER_ACTION).assertIsDisplayed()
    }

    @Test
    fun populatedDocumentsShowsSupportedActionsWithoutUpload() {
        composeRule.setContent {
            DoclyTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        totalDocumentCount = 1,
                        documents = listOf(document())
                    ),
                    onEvent = {},
                    onStartScanner = {}
                )
            }
        }

        composeRule.onNodeWithText("Paper").assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_OPEN_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SHARE_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_FAVORITE_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DELETE_ACTION).assertIsDisplayed()
    }

    @Test
    fun libraryScrollsToHundredthDocument() {
        val documents = (1..100).map { index ->
            document(id = "document-$index", name = "Document $index")
        }

        composeRule.setContent {
            DoclyTheme {
                LibraryScreen(
                    uiState = LibraryUiState(
                        totalDocumentCount = documents.size,
                        documents = documents
                    ),
                    onEvent = {},
                    onStartScanner = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_DOCUMENT_LIST).performScrollToIndex(101)
        composeRule.onNodeWithText("Document 100").assertIsDisplayed()
    }

    private fun document(id: String = "document-id", name: String = "Paper"): DoclyDocument = DoclyDocument(
        id = id,
        name = name,
        type = DocumentType.PDF,
        mimeType = "application/pdf",
        fileRef = FileRef.InternalFile("/documents/paper.pdf"),
        source = DocumentSource.IMPORTED,
        fileSize = 10L,
        createdAt = 1L,
        updatedAt = 1L
    )
}
