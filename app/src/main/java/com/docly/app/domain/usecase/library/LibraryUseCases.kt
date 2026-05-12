package com.docly.app.domain.usecase.library

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDocumentsUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    operator fun invoke(): Flow<List<DoclyDocument>> = documentRepository.observeDocuments()
}

class SearchDocumentsUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    operator fun invoke(query: String): Flow<List<DoclyDocument>> = documentRepository.searchDocuments(query)
}

class ImportDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(uriString: String): AppResult<DoclyDocument> =
        documentRepository.importDocument(uriString)
}

class GetDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String): AppResult<DoclyDocument?> =
        documentRepository.getDocument(documentId)
}

class RenameDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String, name: String): AppResult<Unit> =
        documentRepository.renameDocument(documentId = documentId, name = name)
}

class DeleteDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String): AppResult<Unit> = documentRepository.deleteDocument(documentId)
}

class ToggleFavoriteDocumentUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String, isFavorite: Boolean): AppResult<Unit> =
        documentRepository.toggleFavorite(documentId = documentId, isFavorite = isFavorite)
}

class UpdateLastOpenedUseCase @Inject constructor(private val documentRepository: DocumentRepository) {
    suspend operator fun invoke(documentId: String): AppResult<Unit> = documentRepository.updateLastOpened(documentId)
}

@Deprecated("Use ObserveDocumentsUseCase.")
typealias ObserveSavedDocumentsUseCase = ObserveDocumentsUseCase

@Deprecated("Use SearchDocumentsUseCase.")
typealias SearchSavedDocumentsUseCase = SearchDocumentsUseCase

@Deprecated("Use GetDocumentUseCase.")
typealias GetSavedDocumentUseCase = GetDocumentUseCase

@Deprecated("Use DeleteDocumentUseCase.")
typealias DeleteSavedDocumentUseCase = DeleteDocumentUseCase
