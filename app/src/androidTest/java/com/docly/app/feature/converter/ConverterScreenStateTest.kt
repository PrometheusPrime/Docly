package com.docly.app.feature.converter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConverterScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateShowsLoadingMessage() {
        renderConverter(uiState = ConverterUiState(isLoading = true))

        composeRule.onNodeWithTag(DoclyTestTags.LOADING_CONTENT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Loading documents...")
            .assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsNoConvertibleDocuments() {
        renderConverter(uiState = ConverterUiState(isLoading = false))

        composeRule.onNodeWithTag(DoclyTestTags.EMPTY_CONTENT)
            .assertIsDisplayed()
        composeRule.onNodeWithText("No convertible documents")
            .assertIsDisplayed()
    }

    @Test
    fun readyStateShowsSelectionsAndDispatchesConvert() {
        val receivedEvents = mutableListOf<ConverterUiEvent>()
        renderConverter(uiState = readyState(), onEvent = { event -> receivedEvents += event })

        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_INPUT_LIST)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(DoclyTestTags.CONVERTER_OUTPUT_OPTION)[0]
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_OUTPUT_NAME_FIELD)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_ACTION)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(receivedEvents.contains(ConverterUiEvent.OnConvertClicked))
        }
    }

    @Test
    fun convertingStateDisablesPrimaryActionAndShowsProgress() {
        renderConverter(uiState = readyState().copy(isConverting = true, progress = 10))

        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_PROGRESS)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Converting... 10%")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_ACTION)
            .assertIsNotEnabled()
    }

    @Test
    fun completedStateShowsOpenShareAndDocumentsActions() {
        val receivedEvents = mutableListOf<ConverterUiEvent>()
        renderConverter(
            uiState = readyState().copy(
                completedDocumentId = "output-id",
                completedOutputPath = "/converted/output.pdf"
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_OPEN_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_SHARE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_LIBRARY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(receivedEvents.contains(ConverterUiEvent.OnOpenResultClicked))
            assertTrue(receivedEvents.contains(ConverterUiEvent.OnShareResultClicked))
            assertTrue(receivedEvents.contains(ConverterUiEvent.OnViewDocumentsClicked))
        }
    }

    @Test
    fun largeFontKeepsCompletedActionsReachableOnCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 2f)) {
                DoclyTheme {
                    Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
                        ConverterScreen(
                            uiState = readyState().copy(
                                completedDocumentId = "output-id",
                                completedOutputPath = "/converted/output.pdf"
                            ),
                            onEvent = {},
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.CONVERTER_LIBRARY_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun renderConverter(uiState: ConverterUiState, onEvent: (ConverterUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DoclyTheme {
                ConverterScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun readyState(): ConverterUiState = ConverterUiState(
        documents = listOf(document()),
        selectedDocumentId = "document-id",
        selectedOutputType = DocumentType.PDF,
        supportedOutputTypes = listOf(DocumentType.PDF, DocumentType.HTML),
        outputFileName = "Notes.pdf",
        isLoading = false
    )

    private fun document(): DoclyDocument = DoclyDocument(
        id = "document-id",
        name = "Notes",
        type = DocumentType.TXT,
        mimeType = "text/plain",
        fileRef = FileRef.InternalFile("/documents/notes.txt"),
        source = DocumentSource.CREATED,
        fileSize = 12L,
        createdAt = 1L,
        updatedAt = 1L
    )
}
