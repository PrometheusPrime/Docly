package com.docly.app.feature.scanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
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

    private fun renderScannerContent(uiState: ScannerUiState) {
        composeRule.setContent {
            DoclyTheme {
                ScannerScreenContent(
                    uiState = uiState,
                    onEvent = {},
                    onReviewPlaceholderSession = {},
                    onOpenLibrary = {},
                    onRequestCameraPermission = {},
                    onOpenCameraSettings = {},
                    onImportSinglePhoto = {},
                    onImportMultiplePhotos = {},
                    cameraPreview = {}
                )
            }
        }
    }
}
