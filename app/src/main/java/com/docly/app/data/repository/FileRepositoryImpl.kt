package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.FileRepository
import java.io.File
import java.util.Locale
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    private val appFileDirectories: AppFileDirectories,
    private val dispatcherProvider: DispatcherProvider
) : FileRepository {
    override fun createSessionImagePath(sessionId: String, suffix: String): String =
        createSessionImagePath(sessionId = sessionId, suffix = suffix, extension = JPG_EXTENSION)

    override fun createSessionImagePath(sessionId: String, suffix: String, extension: String): String {
        val safeExtension = extension.toSafeExtension()
        return pathIn(
            directory = appFileDirectories.rawScanDirectory,
            fileName = "raw_${sessionId.toSafeName()}_${suffix.toSafeName()}.$safeExtension"
        )
    }

    override fun createProcessedImagePath(sessionId: String, suffix: String): String = pathIn(
        directory = appFileDirectories.processedScanDirectory,
        fileName = "processed_${sessionId.toSafeName()}_${suffix.toSafeName()}.jpg"
    )

    override fun createThumbnailPath(sessionId: String, suffix: String): String = pathIn(
        directory = appFileDirectories.thumbnailDirectory,
        fileName = "thumb_${sessionId.toSafeName()}_${suffix.toSafeName()}.jpg"
    )

    override fun createPdfPath(fileName: String): String {
        val safeFileName = fileName.toSafeName().let { safeName ->
            if (safeName.endsWith(PDF_EXTENSION)) safeName else "$safeName$PDF_EXTENSION"
        }
        return pathIn(directory = appFileDirectories.pdfDirectory, fileName = safeFileName)
    }

    override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            appFileDirectories.ensureDirectories()

            val usableBytes = appFileDirectories.pdfDirectory.usableSpace
            if (usableBytes < requiredBytes) {
                throw RepositoryFailure(
                    message = "Not enough app storage is available.",
                    category = AppErrorCategory.STORAGE
                )
            }
        }

    override suspend fun deleteFile(path: String): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        if (!deleteOwnedFile(path)) {
            throw RepositoryFailure(
                message = "Could not delete file: $path",
                category = AppErrorCategory.STORAGE
            )
        }
    }

    override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        val failedPaths = paths
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { path -> deleteOwnedFile(path) }

        if (failedPaths.isNotEmpty()) {
            throw RepositoryFailure(
                message = "Could not delete file: ${failedPaths.first()}",
                category = AppErrorCategory.STORAGE
            )
        }
    }

    override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = deleteFiles(page.assetPaths())

    override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> =
        deleteFiles(session.pages.flatMap { page -> page.assetPaths() })

    override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> = deleteFiles(
        listOfNotNull(
            document.pdfPath,
            document.thumbnailPath
        )
    )

    private fun pathIn(directory: File, fileName: String): String {
        directory.mkdirs()
        return File(directory, fileName).absolutePath
    }

    private fun deleteOwnedFile(path: String): Boolean {
        if (path.isBlank()) return true

        val file = File(path)
        if (!file.exists()) return true
        if (file.isDirectory) return false

        return file.delete()
    }

    private fun ScannedPage.assetPaths(): List<String> = listOfNotNull(
        originalImagePath,
        processedImagePath,
        thumbnailPath
    )

    private fun String.toSafeName(): String {
        val safeName = trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')

        return safeName.ifBlank { "file" }
    }

    private fun String.toSafeExtension(): String {
        val safeExtension = trim()
            .lowercase(Locale.US)
            .removePrefix(".")
            .replace(Regex("[^a-z0-9]+"), "")

        return when (safeExtension) {
            "jpeg" -> JPG_EXTENSION
            "" -> JPG_EXTENSION
            else -> safeExtension
        }
    }

    private companion object {
        const val JPG_EXTENSION = "jpg"
        const val PDF_EXTENSION = ".pdf"
    }
}
