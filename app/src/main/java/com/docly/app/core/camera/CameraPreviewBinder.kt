package com.docly.app.core.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface CameraPreviewBinder {
    suspend fun bindPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView): AppResult<CameraPreviewSession>
}

interface CameraPreviewSession {
    val isFlashAvailable: Boolean

    fun setTorchEnabled(enabled: Boolean)

    fun release()
}

class CameraXPreviewBinder(context: Context) : CameraPreviewBinder {
    private val appContext = context.applicationContext

    override suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ): AppResult<CameraPreviewSession> = try {
        val cameraProvider = awaitCameraProvider()
        val cameraSelector = cameraProvider.selectScannerCamera()
            ?: return AppResult.Error(
                message = "No camera is available on this device.",
                category = AppErrorCategory.CAMERA
            )
        val preview = Preview.Builder()
            .build()
            .apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

        AppResult.Success(
            CameraXPreviewSession(
                cameraProvider = cameraProvider,
                preview = preview,
                camera = camera
            )
        )
    } catch (throwable: Throwable) {
        AppResult.Error(
            message = "Camera is unavailable. Please try again.",
            category = AppErrorCategory.CAMERA,
            throwable = throwable
        )
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener(
            {
                try {
                    continuation.resume(cameraProviderFuture.get())
                } catch (exception: ExecutionException) {
                    continuation.resumeWithException(exception.cause ?: exception)
                } catch (throwable: Throwable) {
                    continuation.resumeWithException(throwable)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
        continuation.invokeOnCancellation {
            cameraProviderFuture.cancel(true)
        }
    }

    private fun ProcessCameraProvider.selectScannerCamera(): CameraSelector? = when {
        hasCameraSafely(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
        hasCameraSafely(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> null
    }

    private fun ProcessCameraProvider.hasCameraSafely(cameraSelector: CameraSelector): Boolean =
        runCatching { hasCamera(cameraSelector) }.getOrDefault(false)
}

private class CameraXPreviewSession(
    private val cameraProvider: ProcessCameraProvider,
    private val preview: Preview,
    private val camera: Camera
) : CameraPreviewSession {
    private var isReleased = false

    override val isFlashAvailable: Boolean = camera.cameraInfo.hasFlashUnit()

    override fun setTorchEnabled(enabled: Boolean) {
        if (!isReleased && isFlashAvailable) {
            runCatching {
                camera.cameraControl.enableTorch(enabled)
            }
        }
    }

    override fun release() {
        if (!isReleased) {
            isReleased = true
            runCatching {
                cameraProvider.unbind(preview)
            }
        }
    }
}
