package com.docly.app.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.docly.app.core.camera.CameraPreviewBinder
import com.docly.app.core.camera.CameraXPreviewBinder
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onReviewPlaceholderSession: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentActivity by rememberUpdatedState(context.findActivity())
    val cameraPreviewBinder: CameraPreviewBinder = remember(context.applicationContext) {
        CameraXPreviewBinder(context.applicationContext)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val status = if (granted) {
            CameraPermissionStatus.Granted
        } else if (currentActivity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true) {
            CameraPermissionStatus.Denied
        } else {
            CameraPermissionStatus.PermanentlyDenied
        }
        onEvent(ScannerUiEvent.OnPermissionResult(status))
    }
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { sourceUri ->
        if (sourceUri != null) {
            onEvent(ScannerUiEvent.OnImportPhotosSelected(listOf(sourceUri.toString())))
        }
    }
    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { sourceUris ->
        if (sourceUris.isNotEmpty()) {
            onEvent(ScannerUiEvent.OnImportPhotosSelected(sourceUris.map { sourceUri -> sourceUri.toString() }))
        }
    }

    LaunchedEffect(context) {
        if (context.hasCameraPermission()) {
            onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when {
                    context.hasCameraPermission() -> {
                        currentOnEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
                    }

                    currentUiState.cameraPermissionStatus == CameraPermissionStatus.Granted -> {
                        currentOnEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.NotRequested))
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun launchSinglePhotoPicker() {
        singlePhotoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun launchMultiplePhotoPicker() {
        multiplePhotoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    fun openAppSettings() {
        val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(settingsIntent)
        }
    }

    ScannerScreenContent(
        uiState = uiState,
        onEvent = onEvent,
        onReviewPlaceholderSession = onReviewPlaceholderSession,
        onOpenLibrary = onOpenLibrary,
        onRequestCameraPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenCameraSettings = ::openAppSettings,
        onImportSinglePhoto = ::launchSinglePhotoPicker,
        onImportMultiplePhotos = ::launchMultiplePhotoPicker,
        cameraPreview = {
            CameraPreviewView(
                cameraPreviewBinder = cameraPreviewBinder,
                isFlashEnabled = uiState.isFlashEnabled,
                onCameraReadyChanged = { ready ->
                    onEvent(ScannerUiEvent.OnCameraReadyChanged(ready))
                },
                onCameraPreviewError = { message ->
                    onEvent(ScannerUiEvent.OnCameraPreviewError(message))
                },
                onFlashAvailabilityChanged = { available ->
                    onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(available))
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        modifier = modifier
    )
}

@Composable
fun ScannerScreenContent(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onReviewPlaceholderSession: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenCameraSettings: () -> Unit,
    onImportSinglePhoto: () -> Unit,
    onImportMultiplePhotos: () -> Unit,
    cameraPreview: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Scan",
        screenTestTag = DoclyTestTags.SCANNER_SCREEN,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = onOpenLibrary,
                modifier = Modifier.testTag(DoclyTestTags.OPEN_LIBRARY_ACTION)
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = "Library"
                )
            }
        }
    ) {
        Text(
            text = "Ready to scan",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Start with the camera preview or import existing photos.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CameraPermissionAndPreviewSection(
            uiState = uiState,
            onRequestCameraPermission = onRequestCameraPermission,
            onOpenCameraSettings = onOpenCameraSettings,
            cameraPreview = cameraPreview
        )
        ScannerCaptureControls(
            uiState = uiState,
            onEvent = onEvent
        )
        ImportActions(
            uiState = uiState,
            onImportSinglePhoto = onImportSinglePhoto,
            onImportMultiplePhotos = onImportMultiplePhotos
        )
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            onClick = onReviewPlaceholderSession,
            modifier = Modifier.testTag(DoclyTestTags.SCANNER_REVIEW_ACTION)
        ) {
            Text(text = "Review capture")
        }
        Spacer(modifier = Modifier.height(8.dp))
        DoclyEmptyContent(
            title = "No pages yet",
            message = "Captured and imported pages will appear here.",
            actionLabel = "Import photo",
            onAction = onImportSinglePhoto
        )
    }
}

@Composable
private fun CameraPermissionAndPreviewSection(
    uiState: ScannerUiState,
    onRequestCameraPermission: () -> Unit,
    onOpenCameraSettings: () -> Unit,
    cameraPreview: @Composable () -> Unit
) {
    when (uiState.cameraPermissionStatus) {
        CameraPermissionStatus.Granted -> CameraPreviewPanel(
            isCameraReady = uiState.isCameraReady,
            cameraPreview = cameraPreview
        )

        CameraPermissionStatus.NotRequested -> CameraPermissionMessage(
            title = "Camera access",
            message = "Allow camera access to scan pages with Docly.",
            actionLabel = "Allow camera",
            actionTestTag = DoclyTestTags.CAMERA_PERMISSION_ACTION,
            onAction = onRequestCameraPermission
        )

        CameraPermissionStatus.Denied -> CameraPermissionMessage(
            title = "Camera permission required",
            message = "Camera permission is required to scan pages.",
            actionLabel = "Try again",
            actionTestTag = DoclyTestTags.CAMERA_PERMISSION_ACTION,
            onAction = onRequestCameraPermission
        )

        CameraPermissionStatus.PermanentlyDenied -> CameraPermissionMessage(
            title = "Camera permission blocked",
            message = "Enable camera permission in system settings to scan pages.",
            actionLabel = "Open settings",
            actionTestTag = DoclyTestTags.CAMERA_SETTINGS_ACTION,
            onAction = onOpenCameraSettings
        )
    }
}

@Composable
private fun CameraPreviewPanel(isCameraReady: Boolean, cameraPreview: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 520.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        cameraPreview()
        if (!isCameraReady) {
            Text(
                text = "Starting camera...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CameraPermissionMessage(
    title: String,
    message: String,
    actionLabel: String,
    actionTestTag: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onAction,
            modifier = Modifier.testTag(actionTestTag)
        ) {
            Text(text = actionLabel)
        }
    }
}

@Composable
private fun ScannerCaptureControls(uiState: ScannerUiState, onEvent: (ScannerUiEvent) -> Unit) {
    if (!uiState.isCameraPermissionGranted) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = {
                onEvent(ScannerUiEvent.OnCaptureClicked)
            },
            enabled = uiState.isCameraReady && !uiState.isCapturing,
            modifier = Modifier.testTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
        ) {
            Text(text = if (uiState.isCapturing) "Capturing..." else "Capture")
        }
        if (uiState.isFlashAvailable) {
            Row(
                modifier = Modifier.testTag(DoclyTestTags.CAMERA_FLASH_TOGGLE),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Flash",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = uiState.isFlashEnabled,
                    enabled = uiState.isCameraReady,
                    onCheckedChange = {
                        onEvent(ScannerUiEvent.OnFlashToggleClicked)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportActions(
    uiState: ScannerUiState,
    onImportSinglePhoto: () -> Unit,
    onImportMultiplePhotos: () -> Unit
) {
    Button(
        onClick = onImportSinglePhoto,
        enabled = !uiState.isImporting,
        modifier = Modifier.testTag(DoclyTestTags.IMPORT_SINGLE_PHOTO_ACTION)
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (uiState.isImporting) "Importing..." else "Import photo")
    }
    OutlinedButton(
        onClick = onImportMultiplePhotos,
        enabled = !uiState.isImporting,
        modifier = Modifier.testTag(DoclyTestTags.IMPORT_MULTIPLE_PHOTOS_ACTION)
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Import photos")
    }
}

private fun Context.hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
    this,
    Manifest.permission.CAMERA
) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun ScannerScreenPreview() {
    DoclyTheme {
        ScannerScreenContent(
            uiState = ScannerUiState(),
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
