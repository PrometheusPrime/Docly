package com.docly.app.feature.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.docly.app.core.reader.ExtractedBlockStyle
import com.docly.app.core.reader.ExtractedDocumentBlock
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.domain.model.DocumentType
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Rule
import org.junit.Test

class ReaderScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateIsDisplayed() {
        composeRule.setContent {
            DoclyTheme {
                ReaderScreen(
                    uiState = ReaderUiState(isLoading = true),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.READER_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Opening document...").assertIsDisplayed()
    }

    @Test
    fun pdfStateShowsNavigationAndZoomControls() {
        composeRule.setContent {
            DoclyTheme {
                ReaderScreen(
                    uiState = ReaderUiState(
                        title = "PDF",
                        documentType = DocumentType.PDF,
                        content = ReaderContent.Pdf(
                            pageCount = 2,
                            currentPageIndex = 0,
                            renderedPagePath = null,
                            renderedWidth = 0,
                            renderedHeight = 0,
                            zoom = 1f,
                            targetWidthPx = 1080
                        )
                    ),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.READER_PDF_PREVIOUS_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.READER_PDF_NEXT_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.READER_PDF_ZOOM_IN_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.READER_PDF_PAGE).assertIsDisplayed()
    }

    @Test
    fun textStateShowsReaderControlsAndContent() {
        composeRule.setContent {
            DoclyTheme {
                ReaderScreen(
                    uiState = ReaderUiState(
                        title = "Text",
                        documentType = DocumentType.TXT,
                        content = ReaderContent.Text(
                            lines = listOf("Line one"),
                            nextOffset = null,
                            hasMore = false
                        )
                    ),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.READER_TEXT_SIZE_INCREASE_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.READER_THEME_TOGGLE_ACTION).assertIsDisplayed()
        composeRule.onNodeWithText("Line one").assertIsDisplayed()
    }

    @Test
    fun docxStateShowsSimplifiedBanner() {
        composeRule.setContent {
            DoclyTheme {
                ReaderScreen(
                    uiState = ReaderUiState(
                        title = "DOCX",
                        documentType = DocumentType.DOCX,
                        content = ReaderContent.Docx(
                            blocks = listOf(
                                ExtractedDocumentBlock.Paragraph("Heading", ExtractedBlockStyle.HEADING)
                            ),
                            simplifiedMessage = "Simplified view"
                        )
                    ),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.READER_SIMPLIFIED_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("Heading").assertIsDisplayed()
    }

    @Test
    fun xlsxStateShowsSheetTabsAndRows() {
        composeRule.setContent {
            DoclyTheme {
                ReaderScreen(
                    uiState = ReaderUiState(
                        title = "XLSX",
                        documentType = DocumentType.XLSX,
                        content = ReaderContent.Xlsx(
                            sheets = listOf(XlsxSheetInfo(name = "Sheet", index = 0)),
                            selectedSheetIndex = 0,
                            rows = listOf(listOf("A1", "B1")),
                            nextRowIndex = null,
                            hasMore = false,
                            simplifiedMessage = "Simplified table view"
                        )
                    ),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.READER_XLSX_SHEET_TABS).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.READER_XLSX_TABLE).assertIsDisplayed()
        composeRule.onNodeWithText("A1").assertIsDisplayed()
    }
}
