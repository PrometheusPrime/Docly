package com.docly.app.feature.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class EditorScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorControlsReflectPageBoundariesAndContinueAvailability() {
        renderEditor(
            uiState = EditorUiState(
                sessionId = "session-id",
                pages = listOf(samplePage(id = "first-page"), samplePage(id = "second-page", pageIndex = 1))
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_ADD_PAGE_ACTION).assertIsEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_CONTINUE_ACTION).assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_PAGE_THUMBNAIL)[0].assertIsDisplayed()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_UP_ACTION)[0].assertIsNotEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_DOWN_ACTION)[0].assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_UP_ACTION)[1].assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_DOWN_ACTION)[1].assertIsNotEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_ROTATE_PAGE_ACTION)[0].assertIsEnabled()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_DELETE_PAGE_ACTION)[0].assertIsEnabled()
    }

    @Test
    fun pendingPagesBlockContinue() {
        renderEditor(
            uiState = EditorUiState(
                sessionId = "session-id",
                pages = listOf(samplePage()),
                pendingPageCount = 1
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_CONTINUE_ACTION).assertIsNotEnabled()
    }

    @Test
    fun largeEditorListScrollsToTwentiethPage() {
        renderEditor(
            uiState = EditorUiState(
                sessionId = "session-id",
                pages = (0 until 20).map { index ->
                    samplePage(id = "page-$index", pageIndex = index)
                }
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_PAGE_LIST).performScrollToIndex(22)

        composeRule.onNodeWithText("Page 20").assertIsDisplayed()
    }

    @Test
    fun pageWithoutThumbnailUsesPlaceholderInsteadOfProcessedOrRawImageFallback() {
        renderEditor(
            uiState = EditorUiState(
                sessionId = "session-id",
                pages = listOf(samplePage(thumbnailPath = null))
            )
        )

        composeRule.onNodeWithContentDescription("Page 1 thumbnail").assertIsDisplayed()
    }

    @Test
    fun editorActionClicksDispatchEvents() {
        val receivedEvents = mutableListOf<EditorUiEvent>()
        renderEditor(
            uiState = EditorUiState(
                sessionId = "session-id",
                pages = listOf(samplePage(id = "first-page"), samplePage(id = "second-page", pageIndex = 1))
            ),
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_ADD_PAGE_ACTION).performClick()
        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_CONTINUE_ACTION).performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_DOWN_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_MOVE_PAGE_UP_ACTION)[1]
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_ROTATE_PAGE_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag(DoclyTestTags.EDITOR_DELETE_PAGE_ACTION)[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_DELETE_PAGE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_DELETE_PAGE_CONFIRM_ACTION).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    EditorUiEvent.OnAddPageClicked,
                    EditorUiEvent.OnContinueClicked,
                    EditorUiEvent.OnMovePageDown("first-page"),
                    EditorUiEvent.OnMovePageUp("second-page"),
                    EditorUiEvent.OnRotatePageClicked("first-page"),
                    EditorUiEvent.OnDeletePageClicked("first-page")
                ),
                receivedEvents
            )
        }
    }

    @Test
    fun largeFontKeepsEditorPageActionsReachableOnCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 2f)) {
                DoclyTheme {
                    Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
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
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_DELETE_PAGE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun renderEditor(uiState: EditorUiState, onEvent: (EditorUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DoclyTheme {
                EditorScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun samplePage(
        id: String = "page-id",
        pageIndex: Int = 0,
        thumbnailPath: String? = "/thumb/$id.jpg"
    ): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = pageIndex,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = "/processed/$id.jpg",
        thumbnailPath = thumbnailPath,
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L
    )
}
