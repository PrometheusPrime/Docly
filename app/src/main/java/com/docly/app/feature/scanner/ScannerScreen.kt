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
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.docly.app.app.di.CameraPreviewBinderEntryPoint
import com.docly.app.app.di.DocumentScannerServiceEntryPoint
import com.docly.app.core.camera.CameraPreviewBinder
import com.docly.app.core.camera.CameraPreviewSession
import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.core.result.AppResult
import com.docly.app.core.scanner.DocumentScannerService
import com.docly.app.core.scanner.ScanOptions
import com.docly.app.core.scanner.ScanResultFormat
import com.docly.app.ui.components.DoclyAdaptiveTwoActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.ScanModeSelector
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    onEvent: (ScannerUiEvent) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentActivity by rememberUpdatedState(context.findActivity())
    val coroutineScope = rememberCoroutineScope()
    val documentScannerService: DocumentScannerService = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DocumentScannerServiceEntryPoint::class.java
        ).documentScannerService()
    }
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        handleScannerActivityResult(
            activityResult = activityResult,
            documentScannerService = documentScannerService,
            onEvent = onEvent
        )
    }

    LaunchedEffect(Unit) {
        onEvent(ScannerUiEvent.OnStart)
    }

    MlKitScannerScreenContent(
        uiState = uiState,
        onOpenLibrary = onOpenLibrary,
        onStartScan = {
            val activity = currentActivity
            if (activity == null) {
                onEvent(ScannerUiEvent.OnScannerLaunchFailed("Scanner could not start from this screen."))
                return@MlKitScannerScreenContent
            }

            onEvent(ScannerUiEvent.OnScannerLaunchStarted)
            coroutineScope.launch {
                when (
                    val requestResult = documentScannerService.createScanRequest(
                        activity = activity,
                        options = ScanOptions(
                            allowGalleryImport = true,
                            resultFormats = setOf(ScanResultFormat.JPEG)
                        )
                    )
                ) {
                    is AppResult.Error -> onEvent(ScannerUiEvent.OnScannerLaunchFailed(requestResult.message))

                    is AppResult.Success -> {
                        runCatching {
                            scannerLauncher.launch(requestResult.data)
                        }.onFailure {
                            onEvent(ScannerUiEvent.OnScannerLaunchFailed("Document scanner could not be opened."))
                        }
                    }
                }
            }
        },
        onResumeRecoveredSession = { onEvent(ScannerUiEvent.OnResumeRecoveredSessionClicked) },
        onDiscardRecoveredSession = { onEvent(ScannerUiEvent.OnDiscardRecoveredSessionClicked) },
        modifier = modifier
    )
}

private fun handleScannerActivityResult(
    activityResult: ActivityResult,
    documentScannerService: DocumentScannerService,
    onEvent: (ScannerUiEvent) -> Unit
) {
    if (activityResult.resultCode != Activity.RESULT_OK) {
        onEvent(ScannerUiEvent.OnScanCanceled)
        return
    }

    when (val scanResult = documentScannerService.parseScanResult(activityResult.data)) {
        is AppResult.Error -> onEvent(ScannerUiEvent.OnScannerLaunchFailed(scanResult.message))
        is AppResult.Success -> onEvent(ScannerUiEvent.OnScanResult(scanResult.data.pageImageUris))
    }
}

@Composable
private fun MlKitScannerScreenContent(
    uiState: ScannerUiState,
    onOpenLibrary: () -> Unit,
    onStartScan: () -> Unit,
    onResumeRecoveredSession: () -> Unit,
    onDiscardRecoveredSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDiscardRecoveryConfirmation by rememberSaveable { mutableStateOf(false) }

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
                    contentDescription = "Open library"
                )
            }
        }
    ) {
        Text(
            text = "Scan documents",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        RecoveryPrompt(
            uiState = uiState,
            onResume = onResumeRecoveredSession,
            onDiscard = {
                showDiscardRecoveryConfirmation = true
            }
        )
        ScannerProgressMessage(uiState = uiState)
        Button(
            onClick = onStartScan,
            enabled = !uiState.isLaunchingScanner &&
                !uiState.isImporting &&
                !uiState.isCapturing &&
                !uiState.hasRecoveryPrompt,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = if (uiState.isLaunchingScanner) "Opening scanner..." else "Start scan")
        }
        OutlinedButton(
            onClick = onOpenLibrary,
            modifier = Modifier
                .fillMaxWidth()
                .doclyMinimumTouchTarget()
        ) {
            Text(text = "Documents")
        }
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        DoclyEmptyContent(
            title = "No active scan",
            message = "Scanned pages will open for review after capture.",
            actionLabel = "Start scan",
            onAction = onStartScan
        )
    }

    RecoveryDiscardConfirmationDialog(
        showDialog = showDiscardRecoveryConfirmation && uiState.recoveryPrompt != null,
        isDiscarding = uiState.isDiscardingRecovery,
        onConfirm = {
            showDiscardRecoveryConfirmation = false
            onDiscardRecoveredSession()
        },
        onDismiss = {
            showDiscardRecoveryConfirmation = false
        }
    )
}

@Composable
private fun LegacyCameraScannerScreen(
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
        onEvent(ScannerUiEvent.OnStart)
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
        onAutoCaptureToggle = { enabled ->
            onEvent(ScannerUiEvent.OnAutoCaptureEnabledChanged(enabled))
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
                onPreviewFrameAnalysisChanged = { analysis ->
                    onEvent(ScannerUiEvent.OnPreviewFrameAnalysisChanged(analysis))
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
    modifier: Modifier = Modifier,
    onAutoCaptureToggle: (Boolean) -> Unit = {}
) {
    var showDiscardRecoveryConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.autoCaptureRequestId) {
        if (uiState.autoCaptureRequestId > 0L) {
            onCapture()
        }
    }

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
                    contentDescription = "Open library"
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
        RecoveryPrompt(
            uiState = uiState,
            onResume = {
                onEvent(ScannerUiEvent.OnResumeRecoveredSessionClicked)
            },
            onDiscard = {
                showDiscardRecoveryConfirmation = true
            }
        )
        ScannerProgressMessage(uiState = uiState)
        ScanModeSelector(
            selectedScanMode = uiState.scanMode,
            onScanModeSelected = { scanMode -> onEvent(ScannerUiEvent.OnScanModeChanged(scanMode)) },
            enabled = !uiState.isCapturing && !uiState.isImporting && !uiState.hasRecoveryPrompt
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
            onAutoCaptureToggle = onAutoCaptureToggle,
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

    RecoveryDiscardConfirmationDialog(
        showDialog = showDiscardRecoveryConfirmation && uiState.recoveryPrompt != null,
        isDiscarding = uiState.isDiscardingRecovery,
        onConfirm = {
            showDiscardRecoveryConfirmation = false
            onEvent(ScannerUiEvent.OnDiscardRecoveredSessionClicked)
        },
        onDismiss = {
            showDiscardRecoveryConfirmation = false
        }
    )
}

@Composable
private fun ScannerProgressMessage(uiState: ScannerUiState) {
    val message = when {
        uiState.isCheckingRecovery -> "Checking for unfinished scans..."
        uiState.isDiscardingRecovery -> "Discarding unfinished scan..."
        uiState.isLaunchingScanner -> "Opening scanner..."
        uiState.isCapturing -> "Capturing page..."
        uiState.isImporting -> "Importing scanned pages..."
        else -> return
    }
    DoclyLoadingContent(message = message)
}

@Composable
private fun RecoveryDiscardConfirmationDialog(
    showDialog: Boolean,
    isDiscarding: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = {
            if (!isDiscarding) {
                onDismiss()
            }
        },
        title = { Text(text = "Discard unfinished scan?") },
        text = {
            Text(text = "This removes the recovered pages from this unfinished scan.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDiscarding,
                modifier = Modifier
                    .testTag(DoclyTestTags.RECOVERY_DISCARD_CONFIRM_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = if (isDiscarding) "Discarding..." else "Discard")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDiscarding,
                modifier = Modifier
                    .testTag(DoclyTestTags.RECOVERY_DISCARD_DISMISS_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier
            .testTag(DoclyTestTags.RECOVERY_DISCARD_DIALOG)
            .semantics {
                contentDescription = "Discard unfinished scan confirmation"
            }
    )
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
            qualityHint = uiState.qualityHint,
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
private fun RecoveryPrompt(uiState: ScannerUiState, onResume: () -> Unit, onDiscard: () -> Unit) {
    val prompt = uiState.recoveryPrompt ?: return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.RECOVERY_PROMPT),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resume unfinished scan",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Recovered ${prompt.pageCount.pageCountText()} from your last scan.",
                style = MaterialTheme.typography.bodyMedium
            )
            DoclyAdaptiveTwoActionRow(
                first = { actionModifier ->
                    Button(
                        onClick = onResume,
                        enabled = !uiState.isDiscardingRecovery,
                        modifier = actionModifier.testTag(DoclyTestTags.RECOVERY_RESUME_ACTION)
                    ) {
                        Text(text = "Resume")
                    }
                },
                second = { actionModifier ->
                    OutlinedButton(
                        onClick = onDiscard,
                        enabled = !uiState.isDiscardingRecovery,
                        modifier = actionModifier.testTag(DoclyTestTags.RECOVERY_DISCARD_ACTION)
                    ) {
                        Text(text = if (uiState.isDiscardingRecovery) "Discarding..." else "Discard")
                    }
                }
            )
        }
    }
}

@Composable
private fun CameraPreviewPanel(
    isCameraReady: Boolean,
    previewBoundary: PreviewDocumentBoundary?,
    qualityHint: String?,
    cameraPreview: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 520.dp)
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .semantics {
                contentDescription = if (isCameraReady) "Camera preview" else "Camera preview starting"
            },
        contentAlignment = Alignment.Center
    ) {
        cameraPreview()
        DocumentBoundaryOverlay(previewBoundary = previewBoundary)
        PreviewQualityHint(qualityHint = qualityHint)
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
private fun BoxScope.PreviewQualityHint(qualityHint: String?) {
    if (qualityHint.isNullOrBlank()) return

    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(12.dp)
            .testTag(DoclyTestTags.SCANNER_QUALITY_HINT),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White
    ) {
        Text(
            text = qualityHint,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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
            modifier = Modifier
                .testTag(actionTestTag)
                .doclyMinimumTouchTarget()
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
    onAutoCaptureToggle: (Boolean) -> Unit,
    onFlashToggle: () -> Unit
) {
    if (!uiState.isCameraPermissionGranted) return

    AutoCaptureToggle(uiState = uiState, onAutoCaptureToggle = onAutoCaptureToggle)

    if (!uiState.isFlashAvailable) {
        Button(
            onClick = onCapture,
            enabled = uiState.isCameraReady &&
                isCaptureAvailable &&
                !uiState.isCapturing &&
                !uiState.isImporting &&
                !uiState.hasRecoveryPrompt,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = if (uiState.isCapturing) "Capturing..." else "Capture")
        }
        return
    }

    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            Button(
                onClick = onCapture,
                enabled = uiState.isCameraReady &&
                    isCaptureAvailable &&
                    !uiState.isCapturing &&
                    !uiState.isImporting &&
                    !uiState.hasRecoveryPrompt,
                modifier = actionModifier.testTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
            ) {
                Text(text = if (uiState.isCapturing) "Capturing..." else "Capture")
            }
        },
        second = { actionModifier ->
            Row(
                modifier = actionModifier
                    .testTag(DoclyTestTags.CAMERA_FLASH_TOGGLE)
                    .semantics {
                        contentDescription = if (uiState.isFlashEnabled) "Flash on" else "Flash off"
                    },
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
    )
}

@Composable
private fun AutoCaptureToggle(uiState: ScannerUiState, onAutoCaptureToggle: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (uiState.isAutoCaptureEnabled) {
                    "Auto capture on"
                } else {
                    "Auto capture off"
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto capture",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Switch(
                checked = uiState.isAutoCaptureEnabled,
                enabled =
                    uiState.isCameraReady && !uiState.isCapturing && !uiState.isImporting && !uiState.hasRecoveryPrompt,
                onCheckedChange = onAutoCaptureToggle,
                modifier = Modifier.testTag(DoclyTestTags.SCANNER_AUTO_CAPTURE_TOGGLE)
            )
        }
        if (!uiState.autoCaptureHint.isNullOrBlank()) {
            Text(
                text = uiState.autoCaptureHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportActions(
    uiState: ScannerUiState,
    onImportSinglePhoto: () -> Unit,
    onImportMultiplePhotos: () -> Unit
) {
    DoclyAdaptiveTwoActionRow(
        first = { actionModifier ->
            Button(
                onClick = onImportSinglePhoto,
                enabled = !uiState.isImporting && !uiState.isCapturing && !uiState.hasRecoveryPrompt,
                modifier = actionModifier.testTag(DoclyTestTags.IMPORT_SINGLE_PHOTO_ACTION)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (uiState.isImporting) "Importing..." else "Import photo")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = onImportMultiplePhotos,
                enabled = !uiState.isImporting && !uiState.isCapturing && !uiState.hasRecoveryPrompt,
                modifier = actionModifier.testTag(DoclyTestTags.IMPORT_MULTIPLE_PHOTOS_ACTION)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Import photos")
            }
        }
    )
}

private fun Int.pageCountText(): String = if (this == 1) "1 page" else "$this pages"

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
            onAutoCaptureToggle = {},
            cameraPreview = {}
        )
    }
}
