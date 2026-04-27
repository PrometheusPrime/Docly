package com.docly.app.core.file

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class PdfIntentFactory @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun createOpenIntent(pdfPath: String): AppResult<Intent> {
        val uri = when (val uriResult = pdfUri(pdfPath)) {
            is AppResult.Error -> return uriResult
            is AppResult.Success -> uriResult.data
        }

        return AppResult.Success(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, PDF_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, PDF_CLIP_LABEL, uri)
            }
        )
    }

    fun createShareIntent(pdfPath: String, title: String): AppResult<Intent> {
        val uri = when (val uriResult = pdfUri(pdfPath)) {
            is AppResult.Error -> return uriResult
            is AppResult.Success -> uriResult.data
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, PDF_CLIP_LABEL, uri)
        }

        return AppResult.Success(
            Intent.createChooser(sendIntent, "Share PDF").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = sendIntent.clipData
            }
        )
    }

    private fun pdfUri(pdfPath: String): AppResult<android.net.Uri> {
        if (pdfPath.isBlank()) {
            return storageError("PDF file not found.")
        }

        val pdfFile = File(pdfPath)
        if (!pdfFile.isFile) {
            return storageError("PDF file not found.")
        }

        return try {
            AppResult.Success(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    pdfFile
                )
            )
        } catch (throwable: IllegalArgumentException) {
            AppResult.Error(
                message = "PDF file is not shareable.",
                category = AppErrorCategory.STORAGE,
                throwable = throwable
            )
        }
    }

    private fun storageError(message: String): AppResult.Error = AppResult.Error(
        message = message,
        category = AppErrorCategory.STORAGE
    )

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        const val PDF_CLIP_LABEL = "Docly PDF"
    }
}
