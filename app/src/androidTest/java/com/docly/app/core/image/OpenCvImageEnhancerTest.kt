package com.docly.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ScanMode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvImageEnhancerTest {
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
    fun documentModeProducesHighContrastGrayscaleOutput() = runBlocking {
        val inputPath = enhancementFixture().writeToCacheFile("phase18-input.png")
        val outputFile = File(context.cacheDir, "phase18-document.jpg").apply { delete() }

        val result = enhancer().enhance(
            inputPath = inputPath,
            outputPath = outputFile.absolutePath,
            scanMode = ScanMode.DOCUMENT
        )

        assertTrue(result is AppResult.Success)
        val metrics = outputFile.decodeBitmap().useForMetrics()
        assertEquals(outputFile.absolutePath, (result as AppResult.Success).data)
        assertTrue(outputFile.isFile)
        assertTrue("Expected grayscale output, chroma was ${metrics.averageChroma}", metrics.averageChroma <= 8.0)
        assertTrue(
            "Expected high contrast output, luminance range was ${metrics.luminanceRange}",
            metrics.luminanceRange >= 170.0
        )
    }

    @Test
    fun mixedAndColorModesPreserveMoreColorThanDocumentMode() = runBlocking {
        val inputPath = enhancementFixture().writeToCacheFile("phase18-color-input.png")
        val documentOutput = File(context.cacheDir, "phase18-document-color.jpg").apply { delete() }
        val mixedOutput = File(context.cacheDir, "phase18-mixed.jpg").apply { delete() }
        val colorOutput = File(context.cacheDir, "phase18-color.jpg").apply { delete() }

        enhancer().enhance(
            inputPath = inputPath,
            outputPath = documentOutput.absolutePath,
            scanMode = ScanMode.DOCUMENT
        )
        enhancer().enhance(inputPath = inputPath, outputPath = mixedOutput.absolutePath, scanMode = ScanMode.MIXED)
        enhancer().enhance(inputPath = inputPath, outputPath = colorOutput.absolutePath, scanMode = ScanMode.COLOR)

        val documentChroma = documentOutput.decodeBitmap().useForMetrics().averageChroma
        val mixedChroma = mixedOutput.decodeBitmap().useForMetrics().averageChroma
        val colorChroma = colorOutput.decodeBitmap().useForMetrics().averageChroma

        assertTrue("Expected mixed mode to preserve color.", mixedChroma > documentChroma + 12.0)
        assertTrue("Expected color mode to preserve color.", colorChroma > documentChroma + 12.0)
    }

    private fun enhancer(): OpenCvImageEnhancer = OpenCvImageEnhancer(
        openCvInitializer = DefaultOpenCvInitializer(logger),
        bitmapLoader = AndroidBitmapLoader(dispatcherProvider),
        dispatcherProvider = dispatcherProvider
    )

    private fun enhancementFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(420, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(232, 229, 218))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            strokeWidth = 7f
        }
        repeat(8) { index ->
            val y = 42f + index * 24f
            canvas.drawLine(34f, y, 250f, y, textPaint)
        }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(210, 50, 50)
            canvas.drawRect(282f, 48f, 370f, 108f, this)
            color = Color.rgb(45, 95, 215)
            canvas.drawRect(280f, 126f, 374f, 188f, this)
            color = Color.rgb(40, 160, 100)
            canvas.drawCircle(330f, 235f, 42f, this)
        }
        return bitmap
    }

    private fun Bitmap.writeToCacheFile(fileName: String): String {
        val outputFile = File(context.cacheDir, fileName)
        outputFile.outputStream().use { outputStream ->
            compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        recycle()
        return outputFile.absolutePath
    }

    private fun File.decodeBitmap(): Bitmap = BitmapFactory.decodeFile(absolutePath)
        ?: error("Could not decode $absolutePath.")

    private fun Bitmap.useForMetrics(): ColorMetrics = try {
        colorMetrics()
    } finally {
        recycle()
    }

    private fun Bitmap.colorMetrics(): ColorMetrics {
        var chromaTotal = 0.0
        var sampleCount = 0
        var minLuminance = 255.0
        var maxLuminance = 0.0

        for (y in 0 until height step SAMPLE_STEP_PX) {
            for (x in 0 until width step SAMPLE_STEP_PX) {
                val pixel = getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val luminance = red * RED_LUMINANCE + green * GREEN_LUMINANCE + blue * BLUE_LUMINANCE

                chromaTotal += max(red, max(green, blue)) - min(red, min(green, blue))
                minLuminance = min(minLuminance, luminance)
                maxLuminance = max(maxLuminance, luminance)
                sampleCount += 1
            }
        }

        return ColorMetrics(
            averageChroma = chromaTotal / sampleCount.coerceAtLeast(1),
            luminanceRange = maxLuminance - minLuminance
        )
    }

    private data class ColorMetrics(val averageChroma: Double, val luminanceRange: Double)

    private companion object {
        const val SAMPLE_STEP_PX = 8
        const val RED_LUMINANCE = 0.299
        const val GREEN_LUMINANCE = 0.587
        const val BLUE_LUMINANCE = 0.114
    }
}
