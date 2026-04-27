package com.docly.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.image.AndroidBitmapLoader
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
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
class AndroidPdfGeneratorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun createsReadablePdfWithExpectedPageOrderAndA4Sizing() {
        runBlocking {
            val outputFile = File(context.cacheDir, "phase22/ordered-a4.pdf").reset()
            val firstImage = solidImage(width = 600, height = 900, color = Color.RED)
                .writeToCacheFile("phase22/page-red.png")
            val secondImage = solidImage(width = 900, height = 600, color = Color.BLUE)
                .writeToCacheFile("phase22/page-blue.png")

            val result = generator().generate(
                pageImagePaths = listOf(firstImage, secondImage),
                outputPdfPath = outputFile.absolutePath
            )

            assertTrue(result is AppResult.Success)
            assertEquals(outputFile.absolutePath, (result as AppResult.Success).data)
            assertTrue(outputFile.isFile)
            assertTrue(outputFile.length() > 0L)

            outputFile.openRenderer().use { renderer ->
                assertEquals(2, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(A4_SHORT_EDGE_POINTS, page.width)
                    assertEquals(A4_LONG_EDGE_POINTS, page.height)
                    assertColorNear(page.renderToBitmap().centerColor(), Color.RED)
                }
                renderer.openPage(1).use { page ->
                    assertEquals(A4_LONG_EDGE_POINTS, page.width)
                    assertEquals(A4_SHORT_EDGE_POINTS, page.height)
                    assertColorNear(page.renderToBitmap().centerColor(), Color.BLUE)
                }
            }
        }
    }

    @Test
    fun missingInputImageReturnsPdfErrorAndRemovesOutputFile() {
        runBlocking {
            val outputFile = File(context.cacheDir, "phase22/missing-input.pdf").reset()
            val missingImage = File(context.cacheDir, "phase22/missing-page.png").apply { delete() }

            val result = generator().generate(
                pageImagePaths = listOf(missingImage.absolutePath),
                outputPdfPath = outputFile.absolutePath
            )

            assertTrue(result is AppResult.Error)
            assertEquals(AppErrorCategory.PDF, (result as AppResult.Error).category)
            assertFalse(outputFile.exists())
        }
    }

    private fun generator(): AndroidPdfGenerator = AndroidPdfGenerator(
        bitmapLoader = AndroidBitmapLoader(dispatcherProvider),
        dispatcherProvider = dispatcherProvider
    )

    private fun solidImage(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        canvas.drawText(
            "Docly",
            40f,
            80f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = 44f
            }
        )
        return bitmap
    }

    private fun Bitmap.writeToCacheFile(relativePath: String): String {
        val outputFile = File(context.cacheDir, relativePath).reset()
        outputFile.outputStream().use { outputStream ->
            compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, outputStream)
        }
        recycle()
        return outputFile.absolutePath
    }

    private fun File.reset(): File = apply {
        parentFile?.mkdirs()
        delete()
    }

    private fun File.openRenderer(): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor)
    }

    private fun PdfRenderer.Page.renderToBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun Bitmap.centerColor(): Int = try {
        getPixel(width / 2, height / 2)
    } finally {
        recycle()
    }

    private fun assertColorNear(actualColor: Int, expectedColor: Int) {
        assertTrue(abs(Color.red(actualColor) - Color.red(expectedColor)) <= COLOR_TOLERANCE)
        assertTrue(abs(Color.green(actualColor) - Color.green(expectedColor)) <= COLOR_TOLERANCE)
        assertTrue(abs(Color.blue(actualColor) - Color.blue(expectedColor)) <= COLOR_TOLERANCE)
    }

    private companion object {
        const val A4_SHORT_EDGE_POINTS = 595
        const val A4_LONG_EDGE_POINTS = 842
        const val PNG_QUALITY = 100
        const val COLOR_TOLERANCE = 40
    }
}
