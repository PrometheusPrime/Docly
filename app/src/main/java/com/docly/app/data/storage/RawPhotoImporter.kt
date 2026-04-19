package com.docly.app.data.storage

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.data.repository.RepositoryFailure
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.repository.FileRepository
import java.io.File
import java.io.InputStream
import java.util.Locale

internal data class ImageBounds(val width: Int, val height: Int)

internal interface RawPhotoSource {
    fun openInputStream(sourceUri: String): InputStream?
    fun mimeType(sourceUri: String): String?
}

internal interface ImageBoundsReader {
    fun readBounds(path: String): ImageBounds?
}

internal class RawPhotoImporter(
    private val fileRepository: FileRepository,
    private val rawPhotoSource: RawPhotoSource,
    private val imageBoundsReader: ImageBoundsReader
) {
    suspend fun importRawPhoto(sessionId: String, pageId: String, sourceUri: String): ImportedRawImage {
        val normalizedSourceUri = sourceUri.trim()
        if (normalizedSourceUri.isBlank()) {
            throw RepositoryFailure(
                message = "Select at least one photo to import.",
                category = AppErrorCategory.VALIDATION
            )
        }

        val outputPath = fileRepository.createSessionImagePath(
            sessionId = sessionId,
            suffix = pageId,
            extension = rawPhotoSource.mimeType(normalizedSourceUri).toImageExtension()
        )

        try {
            val inputStream = rawPhotoSource.openInputStream(normalizedSourceUri)
                ?: throw RepositoryFailure(
                    message = "Could not open the selected image.",
                    category = AppErrorCategory.STORAGE
                )

            inputStream.use { input ->
                File(outputPath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val bounds = imageBoundsReader.readBounds(outputPath)
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
                cleanupPartialOutput(outputPath)
                throw RepositoryFailure(
                    message = "Selected image could not be read.",
                    category = AppErrorCategory.VALIDATION
                )
            }

            return ImportedRawImage(path = outputPath, width = bounds.width, height = bounds.height)
        } catch (failure: RepositoryFailure) {
            cleanupPartialOutput(outputPath)
            throw failure
        } catch (throwable: Throwable) {
            cleanupPartialOutput(outputPath)
            throw RepositoryFailure(
                message = "Could not copy the selected image.",
                category = AppErrorCategory.STORAGE,
                cause = throwable
            )
        }
    }

    private suspend fun cleanupPartialOutput(path: String) {
        fileRepository.deleteFile(path)
    }

    private fun String?.toImageExtension(): String {
        val subtype = orEmpty()
            .substringAfter(delimiter = "/", missingDelimiterValue = "")
            .substringBefore(";")
            .lowercase(Locale.US)
            .trim()

        return when (subtype) {
            "jpeg", "jpg", "pjpeg" -> JPG_EXTENSION
            "png" -> "png"
            "webp" -> "webp"
            "heic" -> "heic"
            "heif" -> "heif"
            else -> JPG_EXTENSION
        }
    }

    private companion object {
        const val JPG_EXTENSION = "jpg"
    }
}
