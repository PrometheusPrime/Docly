package com.docly.app.data.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface DoclyStorageManager {
    suspend fun createDocumentFile(name: String, type: DocumentType): AppResult<String>
    suspend fun copyUriToInternalStorage(uriString: String, targetName: String?, type: DocumentType?): AppResult<String>

    suspend fun deleteFile(fileRef: FileRef): AppResult<Unit>
    suspend fun getFileSize(fileRef: FileRef): AppResult<Long>
}

data class SafFileInfo(val displayName: String, val mimeType: String?, val sizeBytes: Long?)

class SafFileInfoReader @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun read(uriString: String): SafFileInfo {
        val uri = Uri.parse(uriString)
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val displayName = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                return SafFileInfo(
                    displayName = displayName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty(),
                    mimeType = mimeType,
                    sizeBytes = size
                )
            }
        }

        return SafFileInfo(
            displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "document",
            mimeType = mimeType,
            sizeBytes = null
        )
    }
}

class AndroidDoclyStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appFileDirectories: AppFileDirectories,
    private val dispatcherProvider: DispatcherProvider
) : DoclyStorageManager {
    override suspend fun createDocumentFile(name: String, type: DocumentType): AppResult<String> =
        withContext(dispatcherProvider.io) {
            runCatching {
                appFileDirectories.ensureDirectories()
                val directory = appFileDirectories.documentDirectory(type)
                directory.mkdirs()
                File(directory, name.toSafeFileName(type.defaultExtension())).uniqueFile().absolutePath
            }.fold(
                onSuccess = { path -> AppResult.Success(path) },
                onFailure = { throwable ->
                    AppResult.Error(
                        message = "Could not create document file.",
                        category = AppErrorCategory.STORAGE,
                        throwable = throwable
                    )
                }
            )
        }

    override suspend fun copyUriToInternalStorage(
        uriString: String,
        targetName: String?,
        type: DocumentType?
    ): AppResult<String> = withContext(dispatcherProvider.io) {
        runCatching {
            val uri = Uri.parse(uriString)
            val documentType = type ?: DocumentType.UNKNOWN
            if (documentType == DocumentType.UNKNOWN) {
                throw StorageFailure("Unsupported document type.")
            }

            appFileDirectories.ensureDirectories()
            val directory = appFileDirectories.documentDirectory(documentType)
            directory.mkdirs()
            val outputFile = File(
                directory,
                (targetName ?: uri.lastPathSegment.orEmpty()).toSafeFileName(documentType.defaultExtension())
            ).uniqueFile()

            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw StorageFailure("Could not open selected document.")

            outputFile.absolutePath
        }.fold(
            onSuccess = { path -> AppResult.Success(path) },
            onFailure = { throwable ->
                AppResult.Error(
                    message = throwable.message ?: "Could not import selected document.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        )
    }

    override suspend fun deleteFile(fileRef: FileRef): AppResult<Unit> = withContext(dispatcherProvider.io) {
        runCatching {
            val file = when (fileRef) {
                is FileRef.InternalFile -> File(fileRef.path)
                is FileRef.ExternalUri -> return@runCatching
            }
            if (file.exists() && file.isFile && !file.delete()) {
                throw StorageFailure("Could not delete file.")
            }
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { throwable ->
                AppResult.Error(
                    message = throwable.message ?: "Could not delete file.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        )
    }

    override suspend fun getFileSize(fileRef: FileRef): AppResult<Long> = withContext(dispatcherProvider.io) {
        runCatching {
            when (fileRef) {
                is FileRef.InternalFile -> File(fileRef.path).length()

                is FileRef.ExternalUri -> {
                    val info = SafFileInfoReader(context).read(fileRef.uri)
                    info.sizeBytes ?: 0L
                }
            }
        }.fold(
            onSuccess = { size -> AppResult.Success(size) },
            onFailure = { throwable ->
                AppResult.Error(
                    message = "Could not read file size.",
                    category = AppErrorCategory.STORAGE,
                    throwable = throwable
                )
            }
        )
    }

    private fun File.uniqueFile(): File {
        if (!exists()) return this
        val baseName = nameWithoutExtension
        val extension = extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        var candidate: File
        do {
            candidate = File(parentFile, "${baseName}_$index$extension")
            index += 1
        } while (candidate.exists())
        return candidate
    }

    private fun String.toSafeFileName(defaultExtension: String): String {
        val rawName = substringAfterLast('/').substringAfterLast(':').ifBlank { "document" }
        val safeName = rawName
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.')
            .ifBlank { "document" }
        val hasExtension = safeName.substringAfterLast('.', "").isNotBlank() && safeName.contains('.')
        return if (hasExtension || defaultExtension.isBlank()) safeName else "$safeName.$defaultExtension"
    }

    private fun DocumentType.defaultExtension(): String = when (this) {
        DocumentType.PDF -> "pdf"
        DocumentType.TXT -> "txt"
        DocumentType.MARKDOWN -> "md"
        DocumentType.HTML -> "html"
        DocumentType.DOCX -> "docx"
        DocumentType.XLSX -> "xlsx"
        DocumentType.CSV -> "csv"
        DocumentType.IMAGE -> "jpg"
        DocumentType.UNKNOWN -> ""
    }
}

private class StorageFailure(message: String) : Exception(message)
