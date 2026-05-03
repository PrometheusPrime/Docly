package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.AppFileDirectories
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.data.local.dao.SavedDocumentDao
import com.docly.app.data.local.dao.ScannedPageDao
import com.docly.app.domain.model.OrphanCleanupResult
import com.docly.app.domain.repository.CleanupRepository
import java.io.File
import javax.inject.Inject

class CleanupRepositoryImpl @Inject constructor(
    private val scannedPageDao: ScannedPageDao,
    private val savedDocumentDao: SavedDocumentDao,
    private val appFileDirectories: AppFileDirectories,
    private val dispatcherProvider: DispatcherProvider
) : CleanupRepository {
    override suspend fun cleanOrphanedFiles(): AppResult<OrphanCleanupResult> = repositoryResult(dispatcherProvider) {
        appFileDirectories.ensureDirectories()

        val referencedPaths = referencedFilePaths()
        val managedFiles = managedDirectories()
            .flatMap { directory -> directory.managedFiles() }
            .distinctBy { file -> file.normalizedPath() }

        var deletedFileCount = 0
        val failedFile = managedFiles
            .filterNot { file -> file.normalizedPath() in referencedPaths }
            .firstOrNull { file ->
                val deleted = file.delete()
                if (deleted) deletedFileCount += 1
                !deleted && file.exists()
            }

        if (failedFile != null) {
            throw RepositoryFailure(
                message = "Could not clean orphaned file: ${failedFile.absolutePath}",
                category = AppErrorCategory.STORAGE
            )
        }

        OrphanCleanupResult(deletedFileCount = deletedFileCount)
    }

    private suspend fun referencedFilePaths(): Set<String> = buildSet {
        scannedPageDao.getAll().forEach { page ->
            addPath(page.originalImagePath)
            addPath(page.processedImagePath)
            addPath(page.thumbnailPath)
        }
        savedDocumentDao.getAll().forEach { document ->
            addPath(document.pdfPath)
            addPath(document.thumbnailPath)
        }
    }

    private fun MutableSet<String>.addPath(path: String?) {
        if (!path.isNullOrBlank()) {
            add(File(path).normalizedPath())
        }
    }

    private fun managedDirectories(): List<File> = listOf(
        appFileDirectories.rawScanDirectory,
        appFileDirectories.processedScanDirectory,
        appFileDirectories.thumbnailDirectory,
        appFileDirectories.pdfDirectory
    )

    private fun File.managedFiles(): List<File> {
        if (!exists()) return emptyList()
        return walkTopDown()
            .filter { file -> file.isFile }
            .toList()
    }

    private fun File.normalizedPath(): String = runCatching { canonicalPath }.getOrElse { absolutePath }
}
