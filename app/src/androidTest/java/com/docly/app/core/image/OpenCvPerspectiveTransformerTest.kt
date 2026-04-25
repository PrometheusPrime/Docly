package com.docly.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.logging.AppLogger
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PointFSerializable
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCvPerspectiveTransformerTest {
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
    fun warpGeneratedSkewedDocumentCreatesRectangularOutput() {
        runBlocking {
            val imagePath = skewedDocumentBitmap().writeToCacheFile("phase16-skewed-document.png")
            val outputDirectory = File(context.cacheDir, "phase16/warped/nested").apply {
                deleteRecursively()
            }
            val outputFile = File(outputDirectory, "warped-document.jpg")

            val result = transformer().warp(
                imagePath = imagePath,
                corners = skewedDocumentCorners(),
                outputPath = outputFile.absolutePath
            )

            assertTrue(result is AppResult.Success)
            val warpResult = (result as AppResult.Success).data
            val outputDimensions = readDimensions(warpResult.outputPath)
            val aspectRatio = warpResult.width.toFloat() / warpResult.height.toFloat()

            assertEquals(outputFile.absolutePath, warpResult.outputPath)
            assertTrue(outputFile.isFile)
            assertTrue(outputDirectory.isDirectory)
            assertEquals(warpResult.width, outputDimensions.width)
            assertEquals(warpResult.height, outputDimensions.height)
            assertTrue(warpResult.width > 0)
            assertTrue(warpResult.height > 0)
            assertTrue(abs(EXPECTED_ASPECT_RATIO - aspectRatio) <= ASPECT_RATIO_TOLERANCE)
        }
    }

    @Test
    fun warpReturnsProcessingErrorForUnreadableInput() {
        runBlocking {
            val outputFile = File(context.cacheDir, "phase16-invalid-output.jpg").apply {
                delete()
            }

            val result = transformer().warp(
                imagePath = File(context.cacheDir, "missing-phase16-input.jpg").absolutePath,
                corners = skewedDocumentCorners(),
                outputPath = outputFile.absolutePath
            )

            assertTrue(result is AppResult.Error)
            assertEquals(AppErrorCategory.PROCESSING, (result as AppResult.Error).category)
            assertFalse(outputFile.exists())
        }
    }

    private fun transformer(): OpenCvPerspectiveTransformer = OpenCvPerspectiveTransformer(
        openCvInitializer = DefaultOpenCvInitializer(logger),
        bitmapLoader = AndroidBitmapLoader(dispatcherProvider),
        dispatcherProvider = dispatcherProvider
    )

    private fun skewedDocumentCorners(): PageCorners = PageCorners(
        topLeft = PointFSerializable(120f, 110f),
        topRight = PointFSerializable(690f, 150f),
        bottomRight = PointFSerializable(640f, 880f),
        bottomLeft = PointFSerializable(100f, 820f)
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

    private fun Bitmap.writeToCacheFile(fileName: String): String {
        val outputFile = File(context.cacheDir, fileName)
        outputFile.outputStream().use { outputStream ->
            compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        recycle()
        return outputFile.absolutePath
    }

    private fun readDimensions(path: String): ImageDimensions {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        return ImageDimensions(width = options.outWidth, height = options.outHeight)
    }

    private companion object {
        const val EXPECTED_ASPECT_RATIO = 0.78f
        const val ASPECT_RATIO_TOLERANCE = 0.12f
    }
}
