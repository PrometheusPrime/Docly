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
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.docly.app.app.di.CameraPreviewBinderEntryPoint
import com.docly.app.core.camera.CameraPreviewBinder
import com.docly.app.core.camera.CameraPreviewSession
import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.ScanModeSelector
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import dagger.hilt.android.EntryPointAccessors

@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentActivity by rememberUpdatedState(context.findActivity())
    val cameraPreviewBinder: CameraPreviewBinder = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CameraPreviewBinderEntryPoint::class.java
        ).cameraPreviewBinder()
    }
    var previewSession: CameraPreviewSession? by remember { mutableStateOf(null) }
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
        onOpenLibrary = onOpenLibrary,
        onRequestCameraPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenCameraSettings = ::openAppSettings,
        onImportSinglePhoto = ::launchSinglePhotoPicker,
        onImportMultiplePhotos = ::launchMultiplePhotoPicker,
        isCaptureAvailable = previewSession != null,
        onCapture = capture@{
            val activeSession = previewSession ?: return@capture
            onEvent(
                ScannerUiEvent.OnCaptureClicked(
                    ScannerCaptureAction { outputPath ->
                        activeSession.captureToFile(outputPath)
                    }
                )
            )
        },
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
                onPreviewSessionChanged = { session ->
                    previewSession = session
                },
                onDocumentBoundaryChanged = { boundary ->
                    onEvent(ScannerUiEvent.OnPreviewDocumentBoundaryChanged(boundary))
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
    onOpenLibrary: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenCameraSettings: () -> Unit,
    onImportSinglePhoto: () -> Unit,
    onImportMultiplePhotos: () -> Unit,
    isCaptureAvailable: Boolean,
    onCapture: () -> Unit,
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
        ScanModeSelector(
            selectedScanMode = uiState.scanMode,
            onScanModeSelected = { scanMode -> onEvent(ScannerUiEvent.OnScanModeChanged(scanMode)) },
            enabled = !uiState.isCapturing && !uiState.isImporting
        )
        CameraPermissionAndPreviewSection(
            uiState = uiState,
            onRequestCameraPermission = onRequestCameraPermission,
            onOpenCameraSettings = onOpenCameraSettings,
            cameraPreview = cameraPreview
        )
        ScannerCaptureControls(
            uiState = uiState,
            isCaptureAvailable = isCaptureAvailable,
            onCapture = onCapture,
            onFlashToggle = {
                onEvent(ScannerUiEvent.OnFlashToggleClicked)
            }
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
            previewBoundary = uiState.previewBoundary,
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
private fun CameraPreviewPanel(
    isCameraReady: Boolean,
    previewBoundary: PreviewDocumentBoundary?,
    cameraPreview: @Composable () -> Unit
) {
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
        DocumentBoundaryOverlay(previewBoundary = previewBoundary)
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
private fun DocumentBoundaryOverlay(previewBoundary: PreviewDocumentBoundary?) {
    if (previewBoundary == null) return

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DoclyTestTags.DOCUMENT_BOUNDARY_OVERLAY)
    ) {
        val imageWidth = previewBoundary.imageWidth.toFloat()
        val imageHeight = previewBoundary.imageHeight.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return@Canvas

        val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
        val horizontalOffset = (size.width - imageWidth * scale) / 2f
        val verticalOffset = (size.height - imageHeight * scale) / 2f

        fun mapX(x: Float): Float = x * scale + horizontalOffset
        fun mapY(y: Float): Float = y * scale + verticalOffset

        val corners = previewBoundary.corners
        val path = Path().apply {
            moveTo(mapX(corners.topLeft.x), mapY(corners.topLeft.y))
            lineTo(mapX(corners.topRight.x), mapY(corners.topRight.y))
            lineTo(mapX(corners.bottomRight.x), mapY(corners.bottomRight.y))
            lineTo(mapX(corners.bottomLeft.x), mapY(corners.bottomLeft.y))
            close()
        }
        drawPath(
            path = path,
            color = Color(0xFF69D6C5),
            style = Stroke(width = 4.dp.toPx())
        )
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
private fun ScannerCaptureControls(
    uiState: ScannerUiState,
    isCaptureAvailable: Boolean,
    onCapture: () -> Unit,
    onFlashToggle: () -> Unit
) {
    if (!uiState.isCameraPermissionGranted) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onCapture,
            enabled = uiState.isCameraReady && isCaptureAvailable && !uiState.isCapturing && !uiState.isImporting,
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
                    onCheckedChange = { onFlashToggle() }
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
        enabled = !uiState.isImporting && !uiState.isCapturing,
        modifier = Modifier.testTag(DoclyTestTags.IMPORT_SINGLE_PHOTO_ACTION)
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (uiState.isImporting) "Importing..." else "Import photo")
    }
    OutlinedButton(
        onClick = onImportMultiplePhotos,
        enabled = !uiState.isImporting && !uiState.isCapturing,
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
