package com.docly.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvDocumentDetectorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }
    private val logger = object : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String, throwable: Throwable?) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    @Test
    fun openCvInitializerSucceeds() {
        val result = DefaultOpenCvInitializer(logger).initialize()

        assertTrue(result is AppResult.Success)
    }

    @Test
    fun detectorFindsGeneratedSkewedDocumentCorners() {
        runBlocking {
            val imagePath = skewedDocumentBitmap().writeToCacheFile("skewed-document.png")
            val detector = detector()

            val result = detector.detect(imagePath)

            assertTrue(result is AppResult.Success)
            val corners = (result as AppResult.Success).data
            assertNotNull(corners)
            corners?.let {
                assertCornerNear(PointFSerializable(120f, 110f), it.topLeft)
                assertCornerNear(PointFSerializable(690f, 150f), it.topRight)
                assertCornerNear(PointFSerializable(640f, 880f), it.bottomRight)
                assertCornerNear(PointFSerializable(100f, 820f), it.bottomLeft)
            }
        }
    }

    @Test
    fun detectorReturnsNullForBlankLowContrastImage() {
        runBlocking {
            val imagePath = blankBitmap().writeToCacheFile("blank-document.png")
            val detector = detector()

            val result = detector.detect(imagePath)

            assertTrue(result is AppResult.Success)
            assertEquals(null, (result as AppResult.Success).data)
        }
    }

    private fun detector(): OpenCvDocumentDetector = OpenCvDocumentDetector(
        openCvInitializer = DefaultOpenCvInitializer(logger),
        bitmapLoader = AndroidBitmapLoader(dispatcherProvider),
        detectionEngine = DocumentBoundaryDetectionEngine(),
        dispatcherProvider = dispatcherProvider
    )

    private fun skewedDocumentBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(55, 58, 62))
        val documentPath = Path().apply {
            moveTo(120f, 110f)
            lineTo(690f, 150f)
            lineTo(640f, 880f)
            lineTo(100f, 820f)
            close()
        }
        canvas.drawPath(
            documentPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
        )
        return bitmap
    }

    private fun blankBitmap(): Bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(120, 120, 120))
    }

    private fun Bitmap.writeToCacheFile(fileName: String): String {
        val outputFile = File(context.cacheDir, fileName)
        outputFile.outputStream().use { outputStream ->
            compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        recycle()
        return outputFile.absolutePath
    }

    private fun assertCornerNear(expected: PointFSerializable, actual: PointFSerializable) {
        assertTrue("Expected x near ${expected.x}, was ${actual.x}", kotlin.math.abs(expected.x - actual.x) <= 90f)
        assertTrue("Expected y near ${expected.y}, was ${actual.y}", kotlin.math.abs(expected.y - actual.y) <= 90f)
    }
}
