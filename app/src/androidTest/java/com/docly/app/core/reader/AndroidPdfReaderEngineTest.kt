package com.docly.app.core.reader

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPdfReaderEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
    }

    @Test
    fun rendersOnePdfPageToCacheImage() {
        runBlocking {
            val pdfFile = File(context.cacheDir, "reader-test/sample.pdf").reset()
            createSinglePagePdf(pdfFile)

            val engine = AndroidPdfReaderEngine(context, dispatcherProvider)
            val info = engine.open(FileRef.InternalFile(pdfFile.absolutePath))
            val rendered = engine.renderPage(
                documentId = "reader-test",
                fileRef = FileRef.InternalFile(pdfFile.absolutePath),
                pageIndex = 0,
                widthPx = 360,
                zoom = 1f
            )

            assertEquals(1, (info as AppResult.Success).data.pageCount)
            val renderedPage = (rendered as AppResult.Success).data
            assertTrue(File(renderedPage.imagePath).isFile)
            assertTrue(renderedPage.width >= 360)
            assertTrue(renderedPage.height > 0)
        }
    }

    private fun createSinglePagePdf(file: File) {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(300, 420, 1).create())
        page.canvas.drawColor(Color.WHITE)
        document.finishPage(page)
        file.outputStream().use { output -> document.writeTo(output) }
        document.close()
    }

    private fun File.reset(): File = apply {
        parentFile?.mkdirs()
        delete()
    }
}
