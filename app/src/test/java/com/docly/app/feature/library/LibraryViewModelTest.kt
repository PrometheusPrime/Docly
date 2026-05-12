package com.docly.app.feature.library

import com.docly.app.core.result.AppResult
import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.usecase.library.DeleteDocumentUseCase
import com.docly.app.domain.usecase.library.ImportDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveDocumentsUseCase
import com.docly.app.domain.usecase.library.RenameDocumentUseCase
import com.docly.app.domain.usecase.library.SearchDocumentsUseCase
import com.docly.app.domain.usecase.library.ToggleFavoriteDocumentUseCase
import com.docly.app.domain.usecase.library.UpdateLastOpenedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observedDocumentsPopulateState() = runTest {
        val first = sampleDocument(id = "first", updatedAt = 2L)
        val second = sampleDocument(id = "second", name = "Notes", type = DocumentType.TXT, updatedAt = 1L)
        val viewModel = viewModel(documents = listOf(first, second))
        advanceUntilIdle()

        assertEquals(listOf(first, second), viewModel.uiState.value.documents)
        assertEquals(2, viewModel.uiState.value.totalDocumentCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun searchAndFavoriteFilterUpdateVisibleDocuments() = runTest {
        val favorite = sampleDocument(id = "favorite", name = "Math", isFavorite = true)
        val regular = sampleDocument(id = "regular", name = "Science")
        val viewModel = viewModel(documents = listOf(favorite, regular))
        advanceUntilIdle()

        viewModel.onEvent(LibraryUiEvent.OnSearchQueryChanged("math"))
        advanceUntilIdle()
        assertEquals(listOf(favorite), viewModel.uiState.value.documents)

        viewModel.onEvent(LibraryUiEvent.OnClearSearchClicked)
        viewModel.onEvent(LibraryUiEvent.OnFavoriteFilterToggled)
        advanceUntilIdle()
        assertEquals(listOf(favorite), viewModel.uiState.value.documents)
    }

    @Test
    fun openShareRenameDeleteAndFavoriteCallRepository() = runTest {
        val document = sampleDocument()
        val repository = FakeDocumentRepository(listOf(document))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        val openEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(LibraryUiEvent.OnOpenDocumentClicked(document.id))
        advanceUntilIdle()
        assertEquals(LibraryUiEffect.OpenDocument("/docs/document-id.pdf", "application/pdf"), openEffect.await())
        assertEquals(listOf(document.id), repository.lastOpenedIds)

        val shareEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(LibraryUiEvent.OnShareDocumentClicked(document.id))
        advanceUntilIdle()
        assertEquals(
            LibraryUiEffect.ShareDocument("/docs/document-id.pdf", "Document", "application/pdf"),
            shareEffect.await()
        )

        viewModel.onEvent(LibraryUiEvent.OnFavoriteDocumentClicked(document.id))
        advanceUntilIdle()
        assertEquals(listOf(document.id to true), repository.favoriteUpdates)

        viewModel.onEvent(LibraryUiEvent.OnRenameDocumentClicked(document.id))
        viewModel.onEvent(LibraryUiEvent.OnRenameDocumentNameChanged("Renamed"))
        viewModel.onEvent(LibraryUiEvent.OnRenameDocumentConfirmed)
        advanceUntilIdle()
        assertEquals(listOf(document.id to "Renamed"), repository.renameUpdates)

        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id))
        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentConfirmed)
        advanceUntilIdle()
        assertEquals(listOf(document.id), repository.deletedIds)
    }

    @Test
    fun importSelectedUriCreatesDocumentAndShowsToast() = runTest {
        val repository = FakeDocumentRepository()
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(LibraryUiEvent.OnImportDocumentSelected("content://document"))
        advanceUntilIdle()

        assertEquals(listOf("content://document"), repository.importedUris)
        assertEquals(LibraryUiEffect.ShowToast("Document imported."), effect.await())
    }

    private fun viewModel(
        documents: List<DoclyDocument> = emptyList(),
        repository: FakeDocumentRepository = FakeDocumentRepository(documents)
    ): LibraryViewModel = LibraryViewModel(
        observeDocumentsUseCase = ObserveDocumentsUseCase(repository),
        searchDocumentsUseCase = SearchDocumentsUseCase(repository),
        importDocumentUseCase = ImportDocumentUseCase(repository),
        renameDocumentUseCase = RenameDocumentUseCase(repository),
        deleteDocumentUseCase = DeleteDocumentUseCase(repository),
        toggleFavoriteDocumentUseCase = ToggleFavoriteDocumentUseCase(repository),
        updateLastOpenedUseCase = UpdateLastOpenedUseCase(repository),
        capabilityResolver = DocumentCapabilityResolver()
    )

    private fun sampleDocument(
        id: String = "document-id",
        name: String = "Document",
        type: DocumentType = DocumentType.PDF,
        updatedAt: Long = 1L,
        isFavorite: Boolean = false
    ): DoclyDocument = DoclyDocument(
        id = id,
        name = name,
        type = type,
        mimeType = if (type == DocumentType.PDF) "application/pdf" else "text/plain",
        fileRef = FileRef.InternalFile("/docs/$id.${if (type == DocumentType.PDF) "pdf" else "txt"}"),
        source = DocumentSource.IMPORTED,
        fileSize = 10L,
        createdAt = 1L,
        updatedAt = updatedAt,
        isFavorite = isFavorite
    )

    private class FakeDocumentRepository(documents: List<DoclyDocument> = emptyList()) : DocumentRepository {
        private val documentsFlow = MutableStateFlow(documents)
        val importedUris = mutableListOf<String>()
        val renameUpdates = mutableListOf<Pair<String, String>>()
        val favoriteUpdates = mutableListOf<Pair<String, Boolean>>()
        val lastOpenedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()

        override fun observeDocuments(): Flow<List<DoclyDocument>> = documentsFlow

        override fun searchDocuments(query: String): Flow<List<DoclyDocument>> = documentsFlow.map { documents ->
            documents.filter { document -> document.name.contains(query, ignoreCase = true) }
        }

        override suspend fun getDocument(documentId: String): AppResult<DoclyDocument?> =
            AppResult.Success(documentsFlow.value.firstOrNull { document -> document.id == documentId })

        override suspend fun importDocument(uriString: String): AppResult<DoclyDocument> {
            importedUris += uriString
            val document = DoclyDocument(
                id = "imported",
                name = "Imported",
                type = DocumentType.PDF,
                mimeType = "application/pdf",
                fileRef = FileRef.InternalFile("/docs/imported.pdf"),
                source = DocumentSource.IMPORTED,
                fileSize = 10L,
                createdAt = 1L,
                updatedAt = 1L
            )
            documentsFlow.value = documentsFlow.value + document
            return AppResult.Success(document)
        }

        override suspend fun upsertDocument(document: DoclyDocument): AppResult<Unit> {
            documentsFlow.value = documentsFlow.value.filterNot { it.id == document.id } + document
            return AppResult.Success(Unit)
        }

        override suspend fun renameDocument(documentId: String, name: String): AppResult<Unit> {
            renameUpdates += documentId to name
            documentsFlow.value = documentsFlow.value.map { document ->
                if (document.id == documentId) document.copy(name = name) else document
            }
            return AppResult.Success(Unit)
        }

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
            deletedIds += documentId
            documentsFlow.value = documentsFlow.value.filterNot { document -> document.id == documentId }
            return AppResult.Success(Unit)
        }

        override suspend fun toggleFavorite(documentId: String, isFavorite: Boolean): AppResult<Unit> {
            favoriteUpdates += documentId to isFavorite
            documentsFlow.value = documentsFlow.value.map { document ->
                if (document.id == documentId) document.copy(isFavorite = isFavorite) else document
            }
            return AppResult.Success(Unit)
        }

        override suspend fun updateLastOpened(documentId: String): AppResult<Unit> {
            lastOpenedIds += documentId
            return AppResult.Success(Unit)
        }
    }

    class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
