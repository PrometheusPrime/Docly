package com.docly.app.domain.usecase.library

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSavedDocumentsUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    operator fun invoke(): Flow<List<SavedDocument>> = documentRepository.observeSavedDocuments()
}

class GetSavedDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String): AppResult<SavedDocument?> =
        documentRepository.getDocument(documentId)
}

class DeleteSavedDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String): AppResult<Unit> = documentRepository.deleteDocument(documentId)
}
