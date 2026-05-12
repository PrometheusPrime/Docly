package com.docly.app.core.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppResult
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHtmlToPdfExporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun rendersHtmlIntoReadablePdf() {
        runBlocking {
            val outputFile = File(context.cacheDir, "phase05/html-export.pdf").reset()

            val result = exporter().generate(
                html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                        <h1>Docly</h1>
                        <p>Created from HTML.</p>
                    </body>
                    </html>
                """.trimIndent(),
                outputPdfPath = outputFile.absolutePath
            )

            assertTrue(result is AppResult.Success)
            assertEquals(outputFile.absolutePath, (result as AppResult.Success).data)
            assertTrue(outputFile.isFile)
            assertTrue(outputFile.length() > 0L)
            outputFile.openRenderer().use { renderer ->
                assertTrue(renderer.pageCount >= 1)
            }
        }
    }

    private fun exporter(): AndroidHtmlToPdfExporter = AndroidHtmlToPdfExporter(
        context = context,
        dispatcherProvider = dispatcherProvider
    )

    private fun File.reset(): File = apply {
        parentFile?.mkdirs()
        delete()
    }

    private fun File.openRenderer(): PdfRenderer {
        val descriptor = ParcelFileDescriptor.open(this, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(descriptor)
    }
}
