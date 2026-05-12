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

class DocumentIntentFactory @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun createOpenIntent(filePath: String, mimeType: String?): AppResult<Intent> {
        val uri = when (val uriResult = fileUri(filePath)) {
            is AppResult.Error -> return uriResult
            is AppResult.Success -> uriResult.data
        }

        return AppResult.Success(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: GENERIC_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, DOCLY_CLIP_LABEL, uri)
            }
        )
    }

    fun createShareIntent(filePath: String, title: String, mimeType: String?): AppResult<Intent> {
        val uri = when (val uriResult = fileUri(filePath)) {
            is AppResult.Error -> return uriResult
            is AppResult.Success -> uriResult.data
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: GENERIC_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, DOCLY_CLIP_LABEL, uri)
        }

        return AppResult.Success(
            Intent.createChooser(sendIntent, "Share document").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = sendIntent.clipData
            }
        )
    }

    private fun fileUri(filePath: String): AppResult<android.net.Uri> {
        if (filePath.isBlank()) {
            return storageError("Document file not found.")
        }

        val file = File(filePath)
        if (!file.isFile) {
            return storageError("Document file not found.")
        }

        return try {
            AppResult.Success(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            )
        } catch (throwable: IllegalArgumentException) {
            AppResult.Error(
                message = "Document file is not shareable.",
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
        const val GENERIC_MIME_TYPE = "application/octet-stream"
        const val DOCLY_CLIP_LABEL = "Docly document"
    }
}

class PdfIntentFactory @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val documentIntentFactory = DocumentIntentFactory(context)

    fun createOpenIntent(pdfPath: String): AppResult<Intent> =
        documentIntentFactory.createOpenIntent(filePath = pdfPath, mimeType = PDF_MIME_TYPE)

    fun createShareIntent(pdfPath: String, title: String): AppResult<Intent> =
        documentIntentFactory.createShareIntent(filePath = pdfPath, title = title, mimeType = PDF_MIME_TYPE)

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}
