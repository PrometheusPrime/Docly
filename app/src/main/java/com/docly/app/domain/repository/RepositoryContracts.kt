package com.docly.app.domain.repository

import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DiagnosticEvent
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.OrphanCleanupResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.model.toDoclyDocument
import com.docly.app.domain.model.toSavedDocumentCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ScanRepository {
    suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession>
    suspend fun getSession(sessionId: String): AppResult<ScanSession?>
    suspend fun getLatestInProgressSession(): AppResult<ScanSession?>
    suspend fun getLatestRecoverableSession(): AppResult<ScanSession?> = getLatestInProgressSession()
    suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit>
    suspend fun addPage(page: ScannedPage): AppResult<Unit>
    suspend fun updatePage(page: ScannedPage): AppResult<Unit>
    suspend fun deletePage(pageId: String): AppResult<Unit>
    suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit>
    suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit>
    suspend fun abandonSession(sessionId: String): AppResult<Unit> =
        updateSessionStatus(sessionId = sessionId, status = ScanSessionStatus.ABANDONED)
}

interface DocumentRepository {
    fun observeDocuments(): Flow<List<DoclyDocument>>
    fun searchDocuments(query: String): Flow<List<DoclyDocument>> = observeDocuments()
    suspend fun getDocument(documentId: String): AppResult<DoclyDocument?>
    suspend fun importDocument(uriString: String): AppResult<DoclyDocument>
    suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit>
    suspend fun renameDocument(documentId: String, name: String): AppResult<Unit>
    suspend fun deleteDocument(documentId: String): AppResult<Unit>
    suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit>
    suspend fun updateLastOpened(documentId: String): AppResult<Unit>

    @Deprecated("Use observeDocuments.")
    fun observeSavedDocuments(): Flow<List<SavedDocument>> = observeDocuments().map { documents ->
        documents.map { document -> document.toSavedDocumentCompat() }
    }

    @Deprecated("Use searchDocuments.")
    fun searchSavedDocuments(query: String): Flow<List<SavedDocument>> = searchDocuments(query).map { documents ->
        documents.map { document -> document.toSavedDocumentCompat() }
    }

    @Deprecated("Use upsertDocument.")
    suspend fun saveDocument(document: SavedDocument): AppResult<Unit> = upsertDocument(document.toDoclyDocument())

    @Deprecated("Use getDocument.")
    suspend fun getSavedDocument(documentId: String): AppResult<SavedDocument?> = when (
        val result = getDocument(documentId)
    ) {
        is AppResult.Error -> result
        is AppResult.Success -> AppResult.Success(result.data?.toSavedDocumentCompat())
    }
}

interface DiagnosticsRepository {
    suspend fun record(event: DiagnosticEvent): AppResult<Unit>
    fun observeRecent(limit: Int = 100): Flow<List<DiagnosticEvent>>
}

interface ImageProcessingRepository {
    suspend fun detectDocument(inputPath: String): AppResult<PageCorners?>

    suspend fun evaluateQuality(inputPath: String, corners: PageCorners?): AppResult<ScanQualityAssessment> =
        AppResult.Success(ScanQualityAssessment.good())

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
    fun createImageDocumentPath(fileName: String): String = createPdfPath(fileName)
    fun createTempImagePath(suffix: String): String = createProcessedImagePath(sessionId = "temp", suffix = suffix)
    suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit>
    suspend fun deleteFile(path: String): AppResult<Unit>
    suspend fun deleteFiles(paths: List<String>): AppResult<Unit>
    suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit>
    suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit>
    suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit>
}

interface CleanupRepository {
    suspend fun cleanOrphanedFiles(): AppResult<OrphanCleanupResult>
}

object StorageReserveBytes {
    const val CAPTURE_BYTES: Long = 25L * 1024L * 1024L
    const val EXPORT_BYTES: Long = 50L * 1024L * 1024L
}
