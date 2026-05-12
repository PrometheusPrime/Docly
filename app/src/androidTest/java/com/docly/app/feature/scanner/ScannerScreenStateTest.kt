package com.docly.app.feature.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionRecoveryDestination
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notRequestedPermissionStateShowsPermissionAction() {
        renderScannerContent(
            ScannerUiState(cameraPermissionStatus = CameraPermissionStatus.NotRequested)
        )

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_PERMISSION_ACTION)
            .assertIsDisplayed()
    }

    @Test
    fun deniedPermissionStateShowsRetryAction() {
        renderScannerContent(
            ScannerUiState(cameraPermissionStatus = CameraPermissionStatus.Denied)
        )

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_PERMISSION_ACTION)
            .assertIsDisplayed()
    }

    @Test
    fun permanentlyDeniedPermissionStateShowsSettingsAction() {
        renderScannerContent(
            ScannerUiState(cameraPermissionStatus = CameraPermissionStatus.PermanentlyDenied)
        )

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_SETTINGS_ACTION)
            .assertIsDisplayed()
    }

    @Test
    fun grantedReadyCameraWithCaptureSessionEnablesCaptureAction() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true
            ),
            isCaptureAvailable = true
        )

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun grantedReadyCameraWithoutCaptureSessionDisablesCaptureAction() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true
            ),
            isCaptureAvailable = false
        )

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun capturingStateShowsProgressLabelAndDisablesCaptureAction() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true,
                isCapturing = true
            ),
            isCaptureAvailable = true
        )

        composeRule.onNodeWithText("Capturing page...")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun detectedPreviewBoundaryShowsOverlay() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true,
                previewBoundary = PreviewDocumentBoundary(
                    corners = PageCorners(
                        topLeft = PointFSerializable(10f, 20f),
                        topRight = PointFSerializable(90f, 25f),
                        bottomRight = PointFSerializable(80f, 180f),
                        bottomLeft = PointFSerializable(15f, 170f)
                    ),
                    imageWidth = 100,
                    imageHeight = 200
                )
            ),
            isCaptureAvailable = true
        )

        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_BOUNDARY_OVERLAY)
            .assertIsDisplayed()
    }

    @Test
    fun previewQualityHintIsDisplayedWhenPresent() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true,
                qualityHint = "Hold steady"
            ),
            isCaptureAvailable = true
        )

        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_QUALITY_HINT)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Hold steady")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun autoCaptureToggleDispatchesEnabledChangeAndShowsHint() {
        val receivedEvents = mutableListOf<ScannerUiEvent>()
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true,
                isAutoCaptureEnabled = false,
                autoCaptureHint = "Auto capture on"
            ),
            isCaptureAvailable = true,
            onAutoCaptureToggle = { enabled ->
                receivedEvents += ScannerUiEvent.OnAutoCaptureEnabledChanged(enabled)
            }
        )

        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_AUTO_CAPTURE_TOGGLE)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithText("Auto capture on")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(listOf(ScannerUiEvent.OnAutoCaptureEnabledChanged(true)), receivedEvents)
        }
    }

    @Test
    fun scannerKeyActionsAndPreviewHaveAccessibleLabels() {
        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true
            ),
            isCaptureAvailable = true
        )

        composeRule.onNodeWithContentDescription("Open library").assertExists()
        composeRule.onNodeWithContentDescription("Camera preview").assertExists()
    }

    @Test
    fun scanModeSelectorShowsModesAndDispatchesSelection() {
        var selectedScanMode: ScanMode? = null

        composeRule.setContent {
            DoclyTheme {
                ScannerScreenContent(
                    uiState = ScannerUiState(scanMode = ScanMode.DOCUMENT),
                    onEvent = { event ->
                        if (event is ScannerUiEvent.OnScanModeChanged) {
                            selectedScanMode = event.scanMode
                        }
                    },
                    onOpenLibrary = {},
                    onRequestCameraPermission = {},
                    onOpenCameraSettings = {},
                    onImportSinglePhoto = {},
                    onImportMultiplePhotos = {},
                    isCaptureAvailable = false,
                    onCapture = {},
                    cameraPreview = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_DOCUMENT_OPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_MIXED_OPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_COLOR_OPTION)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ScanMode.COLOR, selectedScanMode)
        }
    }

    @Test
    fun importStateDisablesScanModeSelection() {
        renderScannerContent(uiState = ScannerUiState(isImporting = true))

        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_DOCUMENT_OPTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_MIXED_OPTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.SCAN_MODE_COLOR_OPTION).assertIsNotEnabled()
    }

    @Test
    fun recoveryPromptDispatchesResumeAndDiscardAndBlocksNewInput() {
        val receivedEvents = mutableListOf<ScannerUiEvent>()

        renderScannerContent(
            uiState = ScannerUiState(
                cameraPermissionStatus = CameraPermissionStatus.Granted,
                isCameraReady = true,
                recoveryPrompt = ScannerRecoveryPrompt(
                    sessionId = "session-id",
                    pageCount = 2,
                    destination = ScanSessionRecoveryDestination.REVIEW
                )
            ),
            isCaptureAvailable = true,
            onEvent = { event -> receivedEvents += event }
        )

        composeRule.onNodeWithTag(DoclyTestTags.RECOVERY_PROMPT).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_CAPTURE_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.IMPORT_SINGLE_PHOTO_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(DoclyTestTags.RECOVERY_RESUME_ACTION).performClick()
        composeRule.onNodeWithTag(DoclyTestTags.RECOVERY_DISCARD_ACTION).performClick()
        composeRule.onNodeWithTag(DoclyTestTags.RECOVERY_DISCARD_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.RECOVERY_DISCARD_CONFIRM_ACTION).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    ScannerUiEvent.OnResumeRecoveredSessionClicked,
                    ScannerUiEvent.OnDiscardRecoveredSessionClicked
                ),
                receivedEvents
            )
        }
    }

    @Test
    fun largeFontKeepsImportActionsReachableOnCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 2f)) {
                DoclyTheme {
                    Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
                        ScannerScreenContent(
                            uiState = ScannerUiState(),
                            onEvent = {},
                            onOpenLibrary = {},
                            onRequestCameraPermission = {},
                            onOpenCameraSettings = {},
                            onImportSinglePhoto = {},
                            onImportMultiplePhotos = {},
                            isCaptureAvailable = false,
                            onCapture = {},
                            cameraPreview = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.IMPORT_MULTIPLE_PHOTOS_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun renderScannerContent(
        uiState: ScannerUiState,
        isCaptureAvailable: Boolean = false,
        onEvent: (ScannerUiEvent) -> Unit = {},
        onAutoCaptureToggle: (Boolean) -> Unit = {}
    ) {
        composeRule.setContent {
            DoclyTheme {
                ScannerScreenContent(
                    uiState = uiState,
                    onEvent = onEvent,
                    onOpenLibrary = {},
                    onRequestCameraPermission = {},
                    onOpenCameraSettings = {},
                    onImportSinglePhoto = {},
                    onImportMultiplePhotos = {},
                    isCaptureAvailable = isCaptureAvailable,
                    onCapture = {},
                    cameraPreview = {},
                    onAutoCaptureToggle = onAutoCaptureToggle
                )
            }
        }
    }
}
