package com.docly.app.data.repository

import com.docly.app.core.common.IdProvider
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.file.FileTypeResolver
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.getOrNull
import com.docly.app.core.time.TimeProvider
import com.docly.app.data.local.dao.DocumentDao
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.data.local.mapper.toEntity
import com.docly.app.data.storage.DoclyStorageManager
import com.docly.app.data.storage.SafFileInfoReader
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.repository.DocumentRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
    private val dispatcherProvider: DispatcherProvider,
    private val storageManager: DoclyStorageManager,
    private val safFileInfoReader: SafFileInfoReader,
    private val fileTypeResolver: FileTypeResolver,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<DoclyDocument>> =
        documentDao.observeAll().map { documents -> documents.map { it.toDomain() } }

    override fun searchDocuments(query: String): Flow<List<DoclyDocument>> {
        val normalizedQuery = query.trim()
        return if (normalizedQuery.isBlank()) {
            observeDocuments()
        } else {
            documentDao.searchByName("%$normalizedQuery%").map { documents -> documents.map { it.toDomain() } }
        }
    }

    override suspend fun getDocument(documentId: String): AppResult<DoclyDocument?> =
        repositoryResult(dispatcherProvider) {
            documentDao.getById(documentId)?.toDomain()
        }

    override suspend fun importDocument(uriString: String): AppResult<DoclyDocument> =
        repositoryResult(dispatcherProvider) {
            val fileInfo = safFileInfoReader.read(uriString)
            val type = fileTypeResolver.resolve(fileName = fileInfo.displayName, mimeType = fileInfo.mimeType)
            if (type == DocumentType.UNKNOWN) {
                throw RepositoryFailure(
                    message = "Docly cannot open this file type yet.",
                    category = AppErrorCategory.VALIDATION
                )
            }

            val copiedPath = storageManager.copyUriToInternalStorage(
                uriString = uriString,
                targetName = fileInfo.displayName,
                type = type
            ).getOrThrow()
            val now = timeProvider.now()
            val document = DoclyDocument(
                id = idProvider.generateId(),
                name = fileInfo.displayName.toDisplayName(type),
                type = type,
                mimeType = fileInfo.mimeType,
                fileRef = FileRef.InternalFile(copiedPath),
                source = DocumentSource.IMPORTED,
                fileSize = File(copiedPath).length().takeIf { it > 0L } ?: fileInfo.sizeBytes ?: 0L,
                pageCount = null,
                createdAt = now,
                updatedAt = now,
                ocrStatus = OcrStatus.NOT_STARTED
            )
            documentDao.upsert(document.toEntity())
            document
        }

    override suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            documentDao.upsert(document.toEntity())
        }

    override suspend fun renameDocument(documentId: String, name: String): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                throw RepositoryFailure(
                    message = "Document name is required.",
                    category = AppErrorCategory.VALIDATION
                )
            }
            documentDao.rename(documentId = documentId, name = trimmedName, updatedAt = timeProvider.now())
        }

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        val document = documentDao.getById(documentId)?.toDomain() ?: return@repositoryResult
        documentDao.delete(document.toEntity())
        storageManager.deleteFile(document.fileRef).throwOnError()
        document.thumbnailPath?.let { thumbnailPath ->
            storageManager.deleteFile(FileRef.InternalFile(thumbnailPath)).throwOnError()
        }
    }

    override suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit> =
        repositoryResult(dispatcherProvider) {
            documentDao.updateFavorite(
                documentId = documentId,
                isFavorite = isFavorite,
                updatedAt = timeProvider.now()
            )
        }

    override suspend fun updateLastOpened(documentId: String): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        documentDao.updateLastOpened(documentId = documentId, openedAt = timeProvider.now())
    }

    private fun String.toDisplayName(type: DocumentType): String {
        val trimmedName = trim().ifBlank { "Document" }
        return if (type == DocumentType.PDF) trimmedName.removeSuffix(".pdf").removeSuffix(".PDF") else trimmedName
    }

    private fun <T> AppResult<T>.getOrThrow(): T = when (this) {
        is AppResult.Error -> throw RepositoryFailure(message = message, category = category, cause = throwable)
        is AppResult.Success -> data
    }
}
