package com.docly.app.data.repository

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppResult
import com.docly.app.data.local.dao.SavedDocumentDao
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.data.local.mapper.toEntity
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepositoryImpl @Inject constructor(
    private val savedDocumentDao: SavedDocumentDao,
    private val dispatcherProvider: DispatcherProvider,
    private val fileRepository: FileRepository
) : DocumentRepository {
    override fun observeSavedDocuments(): Flow<List<SavedDocument>> = savedDocumentDao.observeAll().map { documents ->
        documents.map { it.toDomain() }
    }

    override suspend fun saveDocument(document: SavedDocument): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        savedDocumentDao.insert(document.toEntity())
    }

    override suspend fun getDocument(documentId: String): AppResult<SavedDocument?> =
        repositoryResult(dispatcherProvider) {
            savedDocumentDao.getById(documentId)?.toDomain()
        }

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> = repositoryResult(dispatcherProvider) {
        val document = savedDocumentDao.getById(documentId) ?: return@repositoryResult
        savedDocumentDao.delete(document)
        fileRepository.deleteSavedDocumentAssets(document.toDomain()).throwOnError()
    }
}
