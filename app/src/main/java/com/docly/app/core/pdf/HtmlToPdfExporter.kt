package com.docly.app.core.pdf

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.DoclyPrintCallbacks
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

interface HtmlToPdfExporter {
    suspend fun generate(html: String, outputPdfPath: String): AppResult<String>
}

class AndroidHtmlToPdfExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : HtmlToPdfExporter {
    override suspend fun generate(html: String, outputPdfPath: String): AppResult<String> =
        withContext(dispatcherProvider.main) {
            val outputFile = File(outputPdfPath)
            var webView: WebView? = null
            try {
                if (html.isBlank()) {
                    return@withContext pdfError("Document content is required to create a PDF.")
                }
                if (outputPdfPath.isBlank()) {
                    return@withContext pdfError("A PDF output path is required.")
                }

                outputFile.parentFile?.mkdirs()
                withTimeout(PDF_EXPORT_TIMEOUT_MS) {
                    val activeWebView = createPrintWebView()
                    webView = activeWebView
                    activeWebView.awaitPrintToPdf(html = html, outputFile = outputFile)
                }
                AppResult.Success(outputPdfPath)
            } catch (timeout: TimeoutCancellationException) {
                outputFile.delete()
                pdfError(message = "PDF creation timed out.", throwable = timeout)
            } catch (throwable: Throwable) {
                outputFile.delete()
                pdfError(message = "PDF could not be created.", throwable = throwable)
            } finally {
                webView?.destroy()
            }
        }

    private fun createPrintWebView(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
    }

    private suspend fun WebView.awaitPrintToPdf(html: String, outputFile: File): String =
        suspendCancellableCoroutine { cont ->
            val cancellationSignal = CancellationSignal()
            cont.invokeOnCancellation {
                cancellationSignal.cancel()
                outputFile.delete()
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    val adapter = createPrintDocumentAdapter("Docly document")
                    adapter.writePdf(outputFile = outputFile, cancellationSignal = cancellationSignal) { result ->
                        if (!cont.isActive) return@writePdf
                        result.fold(
                            onSuccess = { cont.resume(outputFile.absolutePath) },
                            onFailure = { throwable ->
                                outputFile.delete()
                                cont.resumeWithException(throwable)
                            }
                        )
                    }
                }
            }
            loadDataWithBaseURL(null, html, HTML_MIME_TYPE, UTF_8_ENCODING, null)
        }

    private fun PrintDocumentAdapter.writePdf(
        outputFile: File,
        cancellationSignal: CancellationSignal,
        onComplete: (Result<String>) -> Unit
    ) {
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("docly_pdf", "Docly PDF", PRINT_DPI, PRINT_DPI))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        onLayout(
            null,
            printAttributes,
            cancellationSignal,
            object : DoclyPrintCallbacks.Layout() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    writeLaidOutPdf(
                        outputFile = outputFile,
                        cancellationSignal = cancellationSignal,
                        onComplete = onComplete
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    onComplete(Result.failure(PdfExportFailure(error?.toString() ?: "PDF layout failed.")))
                }

                override fun onLayoutCancelled() {
                    onComplete(Result.failure(PdfExportFailure("PDF creation was cancelled.")))
                }
            },
            null
        )
    }

    private fun PrintDocumentAdapter.writeLaidOutPdf(
        outputFile: File,
        cancellationSignal: CancellationSignal,
        onComplete: (Result<String>) -> Unit
    ) {
        val descriptor = ParcelFileDescriptor.open(
            outputFile,
            ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY
        )
        onWrite(
            arrayOf(PageRange.ALL_PAGES),
            descriptor,
            cancellationSignal,
            object : DoclyPrintCallbacks.Write() {
                override fun onWriteFinished(pages: Array<out PageRange>?) {
                    descriptor.close()
                    onComplete(Result.success(outputFile.absolutePath))
                }

                override fun onWriteFailed(error: CharSequence?) {
                    descriptor.close()
                    onComplete(Result.failure(PdfExportFailure(error?.toString() ?: "PDF write failed.")))
                }

                override fun onWriteCancelled() {
                    descriptor.close()
                    onComplete(Result.failure(PdfExportFailure("PDF creation was cancelled.")))
                }
            }
        )
    }

    private fun pdfError(message: String, throwable: Throwable? = null): AppResult.Error = AppResult.Error(
        message = message,
        category = AppErrorCategory.PDF,
        throwable = throwable
    )

    private class PdfExportFailure(message: String) : RuntimeException(message)

    private companion object {
        const val HTML_MIME_TYPE = "text/html"
        const val UTF_8_ENCODING = "UTF-8"
        const val PRINT_DPI = 300
        const val PDF_EXPORT_TIMEOUT_MS = 30_000L
    }
}
