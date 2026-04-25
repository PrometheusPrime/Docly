package com.docly.app.domain.repository

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import kotlinx.coroutines.flow.Flow

interface ScanRepository {
    suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession>
    suspend fun getSession(sessionId: String): AppResult<ScanSession?>
    suspend fun getLatestInProgressSession(): AppResult<ScanSession?>
    suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit>
    suspend fun addPage(page: ScannedPage): AppResult<Unit>
    suspend fun updatePage(page: ScannedPage): AppResult<Unit>
    suspend fun deletePage(pageId: String): AppResult<Unit>
    suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit>
    suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit>
}

interface DocumentRepository {
    fun observeSavedDocuments(): Flow<List<SavedDocument>>
    suspend fun saveDocument(document: SavedDocument): AppResult<Unit>
    suspend fun getDocument(documentId: String): AppResult<SavedDocument?>
    suspend fun deleteDocument(documentId: String): AppResult<Unit>
}

interface ImageProcessingRepository {
    suspend fun detectDocument(inputPath: String): AppResult<PageCorners?>

    suspend fun processPage(
        inputPath: String,
        processedOutputPath: String,
        thumbnailOutputPath: String,
        scanMode: ScanMode,
        corners: PageCorners?
    ): AppResult<ProcessedPageResult>

    suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String>
}

interface PdfRepository {
    suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String>
}

interface DevicePhotoRepository {
    suspend fun importRawPhoto(sessionId: String, pageId: String, sourceUri: String): AppResult<ImportedRawImage>
}

interface FileRepository {
    fun createSessionImagePath(sessionId: String, suffix: String): String
    fun createSessionImagePath(sessionId: String, suffix: String, extension: String): String =
        createSessionImagePath(sessionId = sessionId, suffix = suffix)

    fun createProcessedImagePath(sessionId: String, suffix: String): String
    fun createThumbnailPath(sessionId: String, suffix: String): String
    fun createPdfPath(fileName: String): String
    suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit>
    suspend fun deleteFile(path: String): AppResult<Unit>
    suspend fun deleteFiles(paths: List<String>): AppResult<Unit>
    suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit>
    suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit>
    suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit>
}

object StorageReserveBytes {
    const val CAPTURE_BYTES: Long = 25L * 1024L * 1024L
    const val EXPORT_BYTES: Long = 50L * 1024L * 1024L
}
