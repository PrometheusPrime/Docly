package com.docly.app.core.camera

import android.content.Context
import android.os.SystemClock
import android.util.Size as AndroidSize
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.docly.app.core.image.DocumentBoundaryDetectionEngine
import com.docly.app.core.image.LuminanceImage
import com.docly.app.core.image.OpenCvInitializer
import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.image.ScanQualityScorer
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
import org.opencv.core.Size as OpenCvSize
import org.opencv.imgproc.Imgproc

interface CameraPreviewBinder {
    suspend fun bindPreview(lifecycleOwner: LifecycleOwner, previewView: PreviewView): AppResult<CameraPreviewSession>
}

data class CameraCaptureResult(val path: String, val width: Int, val height: Int)

data class PreviewDocumentBoundary(val corners: PageCorners, val imageWidth: Int, val imageHeight: Int)

data class PreviewFrameAnalysis(val boundary: PreviewDocumentBoundary?, val quality: ScanQualityAssessment)

interface CameraPreviewSession {
    val isFlashAvailable: Boolean

    fun setTorchEnabled(enabled: Boolean)

    fun setPreviewFrameAnalysisListener(listener: ((PreviewFrameAnalysis?) -> Unit)?)

    suspend fun captureToFile(outputPath: String): AppResult<CameraCaptureResult>

    fun release()
}

@Singleton
class CameraXPreviewBinder @Inject constructor(
    @ApplicationContext context: Context,
    private val openCvInitializer: OpenCvInitializer,
    private val detectionEngine: DocumentBoundaryDetectionEngine,
    private val scanQualityScorer: ScanQualityScorer,
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
            scanQualityScorer = scanQualityScorer,
            mainExecutor = ContextCompat.getMainExecutor(appContext),
            logger = logger
        )
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(scannerAnalysisResolutionSelector())
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

    private fun scannerAnalysisResolutionSelector(): ResolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                AndroidSize(PREVIEW_ANALYSIS_TARGET_WIDTH_PX, PREVIEW_ANALYSIS_TARGET_HEIGHT_PX),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        )
        .build()

    private companion object {
        const val PREVIEW_ANALYSIS_TARGET_WIDTH_PX = 720
        const val PREVIEW_ANALYSIS_TARGET_HEIGHT_PX = 960
    }
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

    override fun setPreviewFrameAnalysisListener(listener: ((PreviewFrameAnalysis?) -> Unit)?) {
        documentAnalyzer.setPreviewFrameAnalysisListener(listener)
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
            documentAnalyzer.setPreviewFrameAnalysisListener(null)
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
    private val scanQualityScorer: ScanQualityScorer,
    private val mainExecutor: Executor,
    private val logger: AppLogger
) : ImageAnalysis.Analyzer {
    @Volatile
    private var frameAnalysisListener: ((PreviewFrameAnalysis?) -> Unit)? = null

    private var lastAnalysisTimeMillis: Long = 0L

    fun setPreviewFrameAnalysisListener(listener: ((PreviewFrameAnalysis?) -> Unit)?) {
        frameAnalysisListener = listener
    }

    override fun analyze(image: ImageProxy) {
        try {
            val listener = frameAnalysisListener ?: return
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysisTimeMillis < PREVIEW_ANALYSIS_INTERVAL_MS) return
            lastAnalysisTimeMillis = now

            val analysis = analyzeFrame(image)
            mainExecutor.execute {
                listener(analysis)
            }
        } catch (throwable: Throwable) {
            logger.warning(TAG, "Preview frame analysis failed.", throwable)
            mainExecutor.execute {
                frameAnalysisListener?.invoke(null)
            }
        } finally {
            image.close()
        }
    }

    private fun analyzeFrame(image: ImageProxy): PreviewFrameAnalysis? {
        when (val initializationResult = openCvInitializer.initialize()) {
            is AppResult.Error -> {
                logger.warning(TAG, initializationResult.message, initializationResult.throwable)
                return null
            }

            is AppResult.Success -> Unit
        }

        val sourceMat = image.toLuminanceMat() ?: return null
        var uprightMat: Mat? = null
        var analysisMat: Mat? = null
        return try {
            uprightMat = sourceMat.rotatedBy(image.imageInfo.rotationDegrees)
            val frameMat = uprightMat.resizedToMaxLongEdge(PREVIEW_ANALYSIS_MAX_LONG_EDGE_PX)
            analysisMat = frameMat

            val corners = detectionEngine.detect(
                source = frameMat,
                maxLongEdgePx = PREVIEW_ANALYSIS_MAX_LONG_EDGE_PX
            )
            val boundary = corners?.let { detectedCorners ->
                PreviewDocumentBoundary(
                    corners = detectedCorners,
                    imageWidth = frameMat.width(),
                    imageHeight = frameMat.height()
                )
            }
            val quality = scanQualityScorer.score(
                luminanceImage = frameMat.toLuminanceImage(),
                corners = corners
            )

            PreviewFrameAnalysis(boundary = boundary, quality = quality)
        } finally {
            if (analysisMat != null && analysisMat !== uprightMat && analysisMat !== sourceMat) {
                analysisMat.release()
            }
            if (uprightMat != null && uprightMat !== sourceMat) {
                uprightMat.release()
            }
            sourceMat.release()
        }
    }

    private fun ImageProxy.toLuminanceMat(): Mat? {
        val plane = planes.firstOrNull() ?: return null
        val imageWidth = width
        val imageHeight = height
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val sourceBuffer = plane.buffer.duplicate()
        val rowBuffer = ByteArray(rowStride)
        val compactBuffer = ByteArray(imageWidth * imageHeight)
        var destinationOffset = 0

        sourceBuffer.rewind()
        repeat(imageHeight) { rowIndex ->
            val rowOffset = rowIndex * rowStride
            if (rowOffset >= sourceBuffer.limit()) return@repeat

            sourceBuffer.position(rowOffset)
            val rowBytes = minOf(rowStride, sourceBuffer.limit() - rowOffset)
            sourceBuffer.get(rowBuffer, 0, rowBytes)

            if (pixelStride == LUMINANCE_PIXEL_STRIDE) {
                val copyLength = minOf(imageWidth, rowBytes)
                System.arraycopy(rowBuffer, 0, compactBuffer, destinationOffset, copyLength)
                destinationOffset += imageWidth
            } else {
                repeat(imageWidth) { columnIndex ->
                    val sourceOffset = columnIndex * pixelStride
                    compactBuffer[destinationOffset++] = if (sourceOffset < rowBytes) rowBuffer[sourceOffset] else 0
                }
            }
        }

        return Mat(imageHeight, imageWidth, CvType.CV_8UC1).apply {
            put(0, 0, compactBuffer)
        }
    }

    private fun Mat.toLuminanceImage(): LuminanceImage {
        val luminanceBytes = ByteArray(width() * height())
        get(0, 0, luminanceBytes)
        val luminancePixels = IntArray(luminanceBytes.size) { index ->
            luminanceBytes[index].toInt() and BYTE_MASK
        }

        return LuminanceImage(width = width(), height = height(), pixels = luminancePixels)
    }

    private fun Mat.resizedToMaxLongEdge(maxLongEdgePx: Int): Mat {
        val longEdge = maxOf(width(), height())
        if (longEdge <= maxLongEdgePx || maxLongEdgePx <= 0) {
            return this
        }

        val scale = maxLongEdgePx.toDouble() / longEdge.toDouble()
        val resized = Mat()
        Imgproc.resize(this, resized, OpenCvSize(), scale, scale, Imgproc.INTER_AREA)
        return resized
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
        const val PREVIEW_ANALYSIS_MAX_LONG_EDGE_PX = 720
        const val LUMINANCE_PIXEL_STRIDE = 1
        const val BYTE_MASK = 0xFF
        const val FULL_ROTATION_DEGREES = 360
    }
}
