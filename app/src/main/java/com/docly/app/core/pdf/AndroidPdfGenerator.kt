package com.docly.app.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.image.BitmapLoader
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext

class AndroidPdfGenerator @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    private val dispatcherProvider: DispatcherProvider
) : PdfGenerator {
    override suspend fun generate(
        pageImagePaths: List<String>,
        outputPdfPath: String,
        options: PdfGenerationOptions
    ): AppResult<String> = withContext(dispatcherProvider.io) {
        if (pageImagePaths.isEmpty()) {
            return@withContext pdfError("At least one image is required to create a PDF.")
        }
        if (outputPdfPath.isBlank()) {
            return@withContext pdfError("A PDF output path is required.")
        }

        val outputFile = File(outputPdfPath)
        val document = PdfDocument()

        try {
            outputFile.parentFile?.mkdirs()

            pageImagePaths.forEachIndexed { index, imagePath ->
                if (imagePath.isBlank()) {
                    throw PdfGenerationFailure("Page ${index + 1} has no image path.")
                }

                val bitmap = decodeBitmap(
                    imagePath = imagePath,
                    maxLongEdgePx = options.renderQuality.maxLongEdgePx,
                    pageNumber = index + 1
                )

                try {
                    document.renderBitmapPage(
                        bitmap = bitmap,
                        pageNumber = index + 1,
                        pagePolicy = options.pagePolicy
                    )
                } finally {
                    bitmap.recycle()
                }
            }

            outputFile.outputStream().use { outputStream ->
                document.writeTo(outputStream)
            }

            AppResult.Success(outputPdfPath)
        } catch (failure: PdfGenerationFailure) {
            outputFile.delete()
            pdfError(message = failure.message.orEmpty(), throwable = failure.cause)
        } catch (throwable: Throwable) {
            outputFile.delete()
            pdfError(message = "PDF could not be created.", throwable = throwable)
        } finally {
            runCatching { document.close() }
        }
    }

    private suspend fun decodeBitmap(imagePath: String, maxLongEdgePx: Int, pageNumber: Int): Bitmap {
        val decodeResult = bitmapLoader.decode(
            path = imagePath,
            maxWidth = maxLongEdgePx,
            maxHeight = maxLongEdgePx
        )

        return when (decodeResult) {
            is AppResult.Success -> decodeResult.data

            is AppResult.Error -> throw PdfGenerationFailure(
                message = "Page $pageNumber image could not be read.",
                cause = decodeResult.throwable
            )
        }
    }

    private fun PdfDocument.renderBitmapPage(bitmap: Bitmap, pageNumber: Int, pagePolicy: PdfPagePolicy) {
        val pageSize = pagePolicy.pageSizeFor(bitmap)
        val pageInfo = PdfDocument.PageInfo.Builder(pageSize.width, pageSize.height, pageNumber).create()
        val page = startPage(pageInfo)

        try {
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(bitmap, null, bitmap.fitRect(page.canvas), imagePaint)
            finishPage(page)
        } catch (throwable: Throwable) {
            throw PdfGenerationFailure(message = "Page $pageNumber could not be rendered.", cause = throwable)
        }
    }

    private fun PdfPagePolicy.pageSizeFor(bitmap: Bitmap): PdfPageSize = when (this) {
        PdfPagePolicy.A4Fit -> if (bitmap.width >= bitmap.height) {
            PdfPageSize(width = A4_SHORT_EDGE_POINTS, height = A4_LONG_EDGE_POINTS).landscape()
        } else {
            PdfPageSize(width = A4_SHORT_EDGE_POINTS, height = A4_LONG_EDGE_POINTS)
        }
    }

    private fun Bitmap.fitRect(canvas: Canvas): RectF {
        val pageWidth = canvas.width.toFloat()
        val pageHeight = canvas.height.toFloat()
        val scale = minOf(pageWidth / width.toFloat(), pageHeight / height.toFloat())
        val fittedWidth = width * scale
        val fittedHeight = height * scale
        val left = (pageWidth - fittedWidth) / 2f
        val top = (pageHeight - fittedHeight) / 2f

        return RectF(left, top, left + fittedWidth, top + fittedHeight)
    }

    private fun pdfError(message: String, throwable: Throwable? = null): AppResult.Error = AppResult.Error(
        message = message,
        category = AppErrorCategory.PDF,
        throwable = throwable
    )

    private data class PdfPageSize(val width: Int, val height: Int) {
        fun landscape(): PdfPageSize = PdfPageSize(width = height, height = width)
    }

    private class PdfGenerationFailure(override val message: String, override val cause: Throwable? = null) :
        RuntimeException(message, cause)

    private companion object {
        const val A4_SHORT_EDGE_POINTS = 595
        const val A4_LONG_EDGE_POINTS = 842

        val imagePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    }
}
