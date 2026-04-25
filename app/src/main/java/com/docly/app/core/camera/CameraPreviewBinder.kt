package com.docly.app.core.camera

import android.content.Context
import android.os.SystemClock
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.docly.app.core.image.DocumentBoundaryDetectionEngine
import com.docly.app.core.image.OpenCvInitializer
import com.docly.app.core.image.readOrientedImageDimensions
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

interface CameraPreviewBinder {
    suspend fun bindPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView): AppResult<CameraPreviewSession>
}

data class CameraCaptureResult(val path: String, val width: Int, val height: Int)

data class PreviewDocumentBoundary(val corners: PageCorners, val imageWidth: Int, val imageHeight: Int)

interface CameraPreviewSession {
    val isFlashAvailable: Boolean

    fun setTorchEnabled(enabled: Boolean)

    fun setDocumentBoundaryListener(listener: ((PreviewDocumentBoundary?) -> Unit)?)

    suspend fun captureToFile(outputPath: String): AppResult<CameraCaptureResult>

    fun release()
}

@Singleton
class CameraXPreviewBinder @Inject constructor(
    @ApplicationContext context: Context,
    private val openCvInitializer: OpenCvInitializer,
    private val detectionEngine: DocumentBoundaryDetectionEngine,
    private val logger: AppLogger
) : CameraPreviewBinder {
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
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val documentAnalyzer = CameraDocumentAnalyzer(
            openCvInitializer = openCvInitializer,
            detectionEngine = detectionEngine,
            mainExecutor = ContextCompat.getMainExecutor(appContext),
            logger = logger
        )
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
            .apply {
                setAnalyzer(analysisExecutor, documentAnalyzer)
            }

        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )

        AppResult.Success(
            CameraXPreviewSession(
                appContext = appContext,
                cameraProvider = cameraProvider,
                preview = preview,
                imageCapture = imageCapture,
                imageAnalysis = imageAnalysis,
                analysisExecutor = analysisExecutor,
                documentAnalyzer = documentAnalyzer,
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
    private val appContext: Context,
    private val cameraProvider: ProcessCameraProvider,
    private val preview: Preview,
    private val imageCapture: ImageCapture,
    private val imageAnalysis: ImageAnalysis,
    private val analysisExecutor: java.util.concurrent.ExecutorService,
    private val documentAnalyzer: CameraDocumentAnalyzer,
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

    override fun setDocumentBoundaryListener(listener: ((PreviewDocumentBoundary?) -> Unit)?) {
        documentAnalyzer.setDocumentBoundaryListener(listener)
    }

    override suspend fun captureToFile(outputPath: String): AppResult<CameraCaptureResult> {
        if (isReleased) {
            return AppResult.Error(
                message = CAPTURE_FAILED_MESSAGE,
                category = AppErrorCategory.CAMERA
            )
        }

        val outputFile = File(outputPath)
        return try {
            outputFile.parentFile?.mkdirs()
            captureImage(outputFile)

            val captureResult = readCapturedImageBounds(outputFile.absolutePath)
            if (captureResult != null) {
                AppResult.Success(captureResult)
            } else {
                outputFile.delete()
                AppResult.Error(
                    message = CAPTURE_FAILED_MESSAGE,
                    category = AppErrorCategory.CAMERA
                )
            }
        } catch (throwable: Throwable) {
            outputFile.delete()
            AppResult.Error(
                message = CAPTURE_FAILED_MESSAGE,
                category = AppErrorCategory.CAMERA,
                throwable = throwable
            )
        }
    }

    override fun release() {
        if (!isReleased) {
            isReleased = true
            documentAnalyzer.setDocumentBoundaryListener(null)
            runCatching {
                cameraProvider.unbind(preview, imageCapture, imageAnalysis)
            }
            analysisExecutor.shutdown()
        }
    }

    private suspend fun captureImage(outputFile: File): Unit = suspendCancellableCoroutine { continuation ->
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(appContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }

    private fun readCapturedImageBounds(path: String): CameraCaptureResult? {
        val dimensions = readOrientedImageDimensions(path) ?: return null
        return CameraCaptureResult(path = path, width = dimensions.width, height = dimensions.height)
    }

    private companion object {
        const val CAPTURE_FAILED_MESSAGE = "Could not capture image. Please try again."
    }
}

private class CameraDocumentAnalyzer(
    private val openCvInitializer: OpenCvInitializer,
    private val detectionEngine: DocumentBoundaryDetectionEngine,
    private val mainExecutor: Executor,
    private val logger: AppLogger
) : ImageAnalysis.Analyzer {
    @Volatile
    private var boundaryListener: ((PreviewDocumentBoundary?) -> Unit)? = null

    private var lastAnalysisTimeMillis: Long = 0L

    fun setDocumentBoundaryListener(listener: ((PreviewDocumentBoundary?) -> Unit)?) {
        boundaryListener = listener
    }

    override fun analyze(image: ImageProxy) {
        try {
            val listener = boundaryListener ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysisTimeMillis < PREVIEW_ANALYSIS_INTERVAL_MS) return
            lastAnalysisTimeMillis = now

            val boundary = detectBoundary(image)
            mainExecutor.execute {
                listener(boundary)
            }
        } catch (throwable: Throwable) {
            logger.warning(TAG, "Preview document boundary analysis failed.", throwable)
            mainExecutor.execute {
                boundaryListener?.invoke(null)
            }
        } finally {
            image.close()
        }
    }

    private fun detectBoundary(image: ImageProxy): PreviewDocumentBoundary? {
        when (val initializationResult = openCvInitializer.initialize()) {
            is AppResult.Error -> {
                logger.warning(TAG, initializationResult.message, initializationResult.throwable)
                return null
            }

            is AppResult.Success -> Unit
        }

        val sourceMat = image.toRgbaMat() ?: return null
        val uprightMat = sourceMat.rotatedBy(image.imageInfo.rotationDegrees)
        return try {
            val corners = detectionEngine.detect(
                source = uprightMat,
                maxLongEdgePx = PREVIEW_DETECTION_MAX_LONG_EDGE_PX
            ) ?: return null

            PreviewDocumentBoundary(
                corners = corners,
                imageWidth = uprightMat.width(),
                imageHeight = uprightMat.height()
            )
        } finally {
            if (uprightMat !== sourceMat) {
                uprightMat.release()
            }
            sourceMat.release()
        }
    }

    private fun ImageProxy.toRgbaMat(): Mat? {
        val plane = planes.firstOrNull() ?: return null
        val imageWidth = width
        val imageHeight = height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sourceBuffer = plane.buffer
        val rowBuffer = ByteArray(rowStride)
        val compactBuffer = ByteArray(imageWidth * imageHeight * RGBA_CHANNELS)
        var destinationOffset = 0

        sourceBuffer.rewind()
        repeat(imageHeight) { rowIndex ->
            sourceBuffer.position(rowIndex * rowStride)
            val rowBytes = minOf(rowStride, sourceBuffer.remaining())
            sourceBuffer.get(rowBuffer, 0, rowBytes)

            if (pixelStride == RGBA_CHANNELS) {
                System.arraycopy(rowBuffer, 0, compactBuffer, destinationOffset, imageWidth * RGBA_CHANNELS)
                destinationOffset += imageWidth * RGBA_CHANNELS
            } else {
                repeat(imageWidth) { columnIndex ->
                    val sourceOffset = columnIndex * pixelStride
                    repeat(RGBA_CHANNELS) { channelIndex ->
                        compactBuffer[destinationOffset++] = rowBuffer[sourceOffset + channelIndex]
                    }
                }
            }
        }

        return Mat(imageHeight, imageWidth, CvType.CV_8UC4).apply {
            put(0, 0, compactBuffer)
        }
    }

    private fun Mat.rotatedBy(rotationDegrees: Int): Mat {
        val positiveDegrees = (rotationDegrees % FULL_ROTATION_DEGREES) + FULL_ROTATION_DEGREES
        val normalizedDegrees = positiveDegrees % FULL_ROTATION_DEGREES
        if (normalizedDegrees == 0) return this

        val rotated = Mat()
        when (normalizedDegrees) {
            90 -> Core.rotate(this, rotated, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(this, rotated, Core.ROTATE_180)
            270 -> Core.rotate(this, rotated, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> copyTo(rotated)
        }
        return rotated
    }

    private companion object {
        const val TAG = "CameraDocumentAnalyzer"
        const val PREVIEW_ANALYSIS_INTERVAL_MS = 500L
        const val PREVIEW_DETECTION_MAX_LONG_EDGE_PX = 720
        const val RGBA_CHANNELS = 4
        const val FULL_ROTATION_DEGREES = 360
    }
}
