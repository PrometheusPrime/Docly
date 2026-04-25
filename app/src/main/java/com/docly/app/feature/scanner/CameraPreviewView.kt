package com.docly.app.feature.scanner

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.docly.app.core.camera.CameraPreviewBinder
import com.docly.app.core.camera.CameraPreviewSession
import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.toUserMessage
import com.docly.app.ui.util.DoclyTestTags
import kotlinx.coroutines.awaitCancellation

@Composable
fun CameraPreviewView(
    cameraPreviewBinder: CameraPreviewBinder,
    isFlashEnabled: Boolean,
    onCameraReadyChanged: (Boolean) -> Unit,
    onCameraPreviewError: (String) -> Unit,
    onFlashAvailabilityChanged: (Boolean) -> Unit,
    onPreviewSessionChanged: (CameraPreviewSession?) -> Unit,
    onDocumentBoundaryChanged: (PreviewDocumentBoundary?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCameraReadyChanged by rememberUpdatedState(onCameraReadyChanged)
    val currentOnCameraPreviewError by rememberUpdatedState(onCameraPreviewError)
    val currentOnFlashAvailabilityChanged by rememberUpdatedState(onFlashAvailabilityChanged)
    val currentOnPreviewSessionChanged by rememberUpdatedState(onPreviewSessionChanged)
    val currentOnDocumentBoundaryChanged by rememberUpdatedState(onDocumentBoundaryChanged)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var previewSession: CameraPreviewSession? by remember { mutableStateOf(null) }

    LaunchedEffect(cameraPreviewBinder, lifecycleOwner, previewView) {
        previewSession?.release()
        previewSession = null
        currentOnPreviewSessionChanged(null)
        currentOnDocumentBoundaryChanged(null)
        currentOnCameraReadyChanged(false)
        currentOnFlashAvailabilityChanged(false)

        when (val result = cameraPreviewBinder.bindPreview(lifecycleOwner, previewView)) {
            is AppResult.Error -> {
                currentOnCameraPreviewError(result.toUserMessage())
            }

            is AppResult.Success -> {
                val session = result.data
                previewSession = session
                session.setDocumentBoundaryListener { boundary ->
                    currentOnDocumentBoundaryChanged(boundary)
                }
                currentOnPreviewSessionChanged(session)
                currentOnFlashAvailabilityChanged(session.isFlashAvailable)
                currentOnCameraReadyChanged(true)
                try {
                    awaitCancellation()
                } finally {
                    session.setDocumentBoundaryListener(null)
                    session.setTorchEnabled(false)
                    session.release()
                    if (previewSession == session) {
                        previewSession = null
                    }
                    currentOnPreviewSessionChanged(null)
                    currentOnDocumentBoundaryChanged(null)
                    currentOnCameraReadyChanged(false)
                    currentOnFlashAvailabilityChanged(false)
                }
            }
        }
    }

    LaunchedEffect(isFlashEnabled, previewSession) {
        previewSession?.setTorchEnabled(isFlashEnabled)
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.testTag(DoclyTestTags.CAMERA_PREVIEW)
    )
}
