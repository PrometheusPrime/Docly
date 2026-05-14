package com.docly.app.core.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.withContext

class AndroidPdfReaderEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider
) : PdfReaderEngine {
    override suspend fun open(fileRef: FileRef): AppResult<PdfDocumentInfo> = withContext(dispatcherProvider.io) {
        readerResult {
            fileRef.openPdfRenderer().use { renderer ->
                PdfDocumentInfo(pageCount = renderer.pageCount)
            }
        }
    }

    override suspend fun renderPage(
        documentId: String,
        fileRef: FileRef,
        pageIndex: Int,
        widthPx: Int,
        zoom: Float
    ): AppResult<RenderedPdfPage> = withContext(dispatcherProvider.io) {
        readerResult {
            val targetWidth = widthPx.coerceIn(MIN_RENDER_WIDTH_PX, MAX_TARGET_WIDTH_PX)
            val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
            val cacheFile = cacheFile(
                documentId = documentId,
                pageIndex = pageIndex,
                widthPx = targetWidth,
                zoom = safeZoom
            )
            if (cacheFile.isFile) {
                return@readerResult RenderedPdfPage(
                    pageIndex = pageIndex,
                    width = targetWidth,
                    height = 0,
                    imagePath = cacheFile.absolutePath
                )
            }

            fileRef.openPdfRenderer().use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) {
                    throw ReaderFailure("PDF page is not available.", AppErrorCategory.VALIDATION)
                }

                renderer.openPage(pageIndex).use { page ->
                    val pageSize = cappedRenderSize(
                        requestedWidth = (targetWidth * safeZoom).toInt().coerceAtLeast(MIN_RENDER_WIDTH_PX),
                        pageWidth = page.width,
                        pageHeight = page.height
                    )
                    val bitmap = try {
                        Bitmap.createBitmap(pageSize.width, pageSize.height, Bitmap.Config.ARGB_8888)
                    } catch (outOfMemory: OutOfMemoryError) {
                        throw ReaderFailure(
                            message = "This PDF page is too large to render on this device.",
                            category = AppErrorCategory.PROCESSING,
                            cause = outOfMemory
                        )
                    }
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    bitmap.recycle()
                    RenderedPdfPage(
                        pageIndex = pageIndex,
                        width = pageSize.width,
                        height = pageSize.height,
                        imagePath = cacheFile.absolutePath
                    )
                }
            }
        }
    }

    private fun FileRef.openPdfRenderer(): PdfRenderer {
        val file = requireInternalFile()
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(descriptor)
        } catch (throwable: Throwable) {
            descriptor.close()
            throw throwable
        }
    }

    private fun cacheFile(documentId: String, pageIndex: Int, widthPx: Int, zoom: Float): File {
        val cacheName = buildString {
            append(documentId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "document" })
            append("_p")
            append(pageIndex)
            append("_w")
            append(widthPx)
            append("_z")
            append((zoom * 100).toInt())
            append(".png")
        }
        return File(File(context.cacheDir, PDF_CACHE_DIRECTORY), cacheName)
    }

    private fun cappedRenderSize(requestedWidth: Int, pageWidth: Int, pageHeight: Int): RenderSize {
        val initialWidth = requestedWidth.coerceAtMost(MAX_RENDER_WIDTH_PX)
        val initialHeight = ((initialWidth.toFloat() / pageWidth.toFloat()) * pageHeight.toFloat())
            .toInt()
            .coerceAtLeast(1)
        val initialPixels = initialWidth.toLong() * initialHeight.toLong()
        if (initialPixels <= MAX_RENDER_PIXELS) {
            return RenderSize(width = initialWidth, height = initialHeight)
        }

        val scale = sqrt(MAX_RENDER_PIXELS.toDouble() / initialPixels.toDouble()).coerceAtMost(1.0)
        return RenderSize(
            width = (initialWidth * scale).toInt().coerceAtLeast(MIN_RENDER_WIDTH_PX),
            height = (initialHeight * scale).toInt().coerceAtLeast(1)
        )
    }

    private data class RenderSize(val width: Int, val height: Int)

    private companion object {
        const val PDF_CACHE_DIRECTORY = "reader/pdf"
        const val MIN_RENDER_WIDTH_PX = 320
        const val MAX_TARGET_WIDTH_PX = 1600
        const val MAX_RENDER_WIDTH_PX = 2400
        const val MAX_RENDER_PIXELS = 5_500_000L
        const val MIN_ZOOM = 0.75f
        const val MAX_ZOOM = 3f
    }
}
