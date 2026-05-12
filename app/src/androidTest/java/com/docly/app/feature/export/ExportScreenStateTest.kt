package com.docly.app.feature.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateShowsPreparingMessage() {
        renderExport(uiState = ExportUiState(sessionId = SESSION_ID, isLoading = true))

        composeRule.onNodeWithTag(DoclyTestTags.LOADING_CONTENT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Preparing export...")
            .assertIsDisplayed()
    }

    @Test
    fun notReadyStateShowsBlockingError() {
        renderExport(
            uiState = ExportUiState(
                sessionId = SESSION_ID,
                errorMessage = "Process every page before export."
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.ERROR_CONTENT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Export is not ready")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Process every page before export.")
            .assertIsDisplayed()
    }

    @Test
    fun readyStateShowsSummaryAndDispatchesExport() {
        val receivedEvents = mutableListOf<ExportUiEvent>()
        renderExport(uiState = readyState(), onEvent = { event -> receivedEvents += event })

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_SUMMARY)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_FILENAME)
            .assertIsDisplayed()
        composeRule.onNodeWithText("grade_10_math_2026_past_paper_1.pdf")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Grade 10 - Math - 2026 - Past Paper 1")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_PDF_ACTION)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(ExportUiEvent.OnExportClicked), receivedEvents)
        }
    }

    @Test
    fun exportingStateDisablesPrimaryAction() {
        renderExport(uiState = readyState().copy(isExporting = true))

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_PDF_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Exporting...")
            .assertIsDisplayed()
    }

    @Test
    fun exportedStateShowsOpenShareAndLibraryActions() {
        val receivedEvents = mutableListOf<ExportUiEvent>()
        renderExport(
            uiState = readyState().copy(
                isExportReady = false,
                exportedDocumentId = "document-id",
                exportedPdfPath = "/pdf/document.pdf"
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_PDF_ACTION)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_OPEN_ACTION)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_SHARE_ACTION)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_LIBRARY_ACTION)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ExportUiEvent.OnOpenPdfClicked,
                    ExportUiEvent.OnSharePdfClicked,
                    ExportUiEvent.OnOpenLibraryClicked
                ),
                receivedEvents
            )
        }
    }

    @Test
    fun largeFontKeepsExportCompletedActionsReachableOnCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 2f)) {
                DoclyTheme {
                    Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
                        ExportScreen(
                            uiState = readyState().copy(exportedPdfPath = "/pdf/document.pdf"),
                            onEvent = {},
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_LIBRARY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun renderExport(uiState: ExportUiState, onEvent: (ExportUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DoclyTheme {
                ExportScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun readyState(): ExportUiState = ExportUiState(
        sessionId = SESSION_ID,
        fileName = "grade_10_math_2026_past_paper_1.pdf",
        title = "grade_10_math_2026_past_paper_1",
        metadataSummary = "Grade 10 - Math - 2026 - Past Paper 1",
        pageCount = 2,
        isExportReady = true
    )

    private companion object {
        const val SESSION_ID = "session-id"
    }
}
