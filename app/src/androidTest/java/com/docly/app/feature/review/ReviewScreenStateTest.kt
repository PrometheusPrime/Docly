package com.docly.app.feature.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ScanMode
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cropEditorShowsOverlayAndCornerHandles() {
        renderReviewContent(uiState = cropUiState())

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_OVERLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_TOP_LEFT_HANDLE).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_TOP_RIGHT_HANDLE).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_BOTTOM_RIGHT_HANDLE).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_BOTTOM_LEFT_HANDLE).assertIsDisplayed()
    }

    @Test
    fun cropActionsReflectResetAndApplyAvailability() {
        renderReviewContent(
            uiState = cropUiState(
                detectedCorners = null,
                editableCorners = fullImageCorners(imageWidth = 1000, imageHeight = 1400)
            )
        )

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_RESET_DETECTED_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_RESET_FULL_IMAGE_ACTION).assertIsEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_APPLY_CROP_ACTION).assertIsEnabled()
    }

    @Test
    fun processingStateDisablesCropActions() {
        renderReviewContent(uiState = cropUiState(isProcessing = true))

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_RESET_DETECTED_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_RESET_FULL_IMAGE_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_APPLY_CROP_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_DOCUMENT_OPTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_MIXED_OPTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_COLOR_OPTION).assertIsNotEnabled()
    }

    @Test
    fun scanModeSelectorShowsModesAndDispatchesSelection() {
        var selectedScanMode: ScanMode? = null

        composeRule.setContent {
            DoclyTheme {
                ReviewScreen(
                    uiState = cropUiState(selectedScanMode = ScanMode.DOCUMENT),
                    onEvent = { event ->
                        if (event is ReviewUiEvent.OnScanModeChanged) {
                            selectedScanMode = event.scanMode
                        }
                    },
                    onEditPages = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_DOCUMENT_OPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_MIXED_OPTION)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ScanMode.MIXED, selectedScanMode)
        }
    }

    @Test
    fun draggingCornerHandleDispatchesChangedCorners() {
        val initialCorners = sampleCorners()
        var changedCorners: PageCorners? = null

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    cropUiState(
                        detectedCorners = initialCorners,
                        editableCorners = initialCorners
                    )
                )
            }
            DoclyTheme {
                ReviewScreen(
                    uiState = uiState,
                    onEvent = { event ->
                        if (event is ReviewUiEvent.OnCornersChanged) {
                            changedCorners = event.corners
                            uiState = uiState.copy(editableCorners = event.corners)
                        }
                    },
                    onEditPages = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_CROP_TOP_LEFT_HANDLE)
            .performTouchInput {
                down(center)
                moveBy(Offset(18f, 12f))
                up()
            }

        composeRule.runOnIdle {
            assertNotNull(changedCorners)
            assertNotEquals(initialCorners.topLeft, changedCorners?.topLeft)
        }
    }

    private fun renderReviewContent(uiState: ReviewUiState) {
        composeRule.setContent {
            DoclyTheme {
                ReviewScreen(
                    uiState = uiState,
                    onEvent = {},
                    onEditPages = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun cropUiState(
        detectedCorners: PageCorners? = sampleCorners(),
        editableCorners: PageCorners? = sampleCorners(),
        isProcessing: Boolean = false,
        selectedScanMode: ScanMode = ScanMode.DOCUMENT,
        appliedScanMode: ScanMode = ScanMode.DOCUMENT
    ): ReviewUiState = ReviewUiState(
        sessionId = "session-id",
        currentPageId = "page-id",
        rawImagePath = "/raw/page.jpg",
        imageWidth = 1000,
        imageHeight = 1400,
        selectedScanMode = selectedScanMode,
        appliedScanMode = appliedScanMode,
        detectedCorners = detectedCorners,
        editableCorners = editableCorners,
        isProcessing = isProcessing
    )

    private fun sampleCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(80f, 100f),
        topRight = PointFSerializable(900f, 120f),
        bottomRight = PointFSerializable(880f, 1280f),
        bottomLeft = PointFSerializable(100f, 1300f)
    )
}
