package com.docly.app.feature.library

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.library.ObserveSavedDocumentsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun observedDocumentsPopulateLibraryState() = runTest {
        val firstDocument = sampleDocument(id = "first-document", createdAt = 2L)
        val secondDocument = sampleDocument(id = "second-document", title = "Science Paper", createdAt = 1L)
        val viewModel = viewModel(documents = listOf(firstDocument, secondDocument))
        advanceUntilIdle()

        assertEquals(listOf(firstDocument, secondDocument), viewModel.uiState.value.documents)
        assertEquals(2, viewModel.uiState.value.totalDocumentCount)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun searchFiltersDocumentsAcrossTitleAndMetadataCaseInsensitively() = runTest {
        val mathDocument = sampleDocument(
            id = "math-document",
            title = "Algebra Revision",
            metadata = sampleMetadata(subject = "Mathematics", paperNumber = "Paper 1")
        )
        val scienceDocument = sampleDocument(
            id = "science-document",
            title = "Science Paper",
            metadata = sampleMetadata(grade = "Grade 11", subject = "Science", year = 2025, paperType = "Mock")
        )
        val viewModel = viewModel(documents = listOf(mathDocument, scienceDocument))
        advanceUntilIdle()

        viewModel.onEvent(LibraryUiEvent.OnSearchQueryChanged("MATH"))
        assertEquals(listOf(mathDocument), viewModel.uiState.value.documents)

        viewModel.onEvent(LibraryUiEvent.OnSearchQueryChanged("2025"))
        assertEquals(listOf(scienceDocument), viewModel.uiState.value.documents)

        viewModel.onEvent(LibraryUiEvent.OnSearchQueryChanged("paper 1"))
        assertEquals(listOf(mathDocument), viewModel.uiState.value.documents)
    }

    @Test
    fun emptySearchResultsKeepTotalDocumentCount() = runTest {
        val viewModel = viewModel(documents = listOf(sampleDocument()))
        advanceUntilIdle()

        viewModel.onEvent(LibraryUiEvent.OnSearchQueryChanged("biology"))

        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertEquals(1, viewModel.uiState.value.totalDocumentCount)
        assertEquals("biology", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun openShareAndMissingDocumentEmitEffects() = runTest {
        val document = sampleDocument()
        val viewModel = viewModel(documents = listOf(document))
        advanceUntilIdle()

        val openEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(LibraryUiEvent.OnOpenDocumentClicked(document.id))
        advanceUntilIdle()
        assertEquals(LibraryUiEffect.OpenPdf(document.pdfPath), openEffect.await())

        val shareEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(LibraryUiEvent.OnShareDocumentClicked(document.id))
        advanceUntilIdle()
        assertEquals(LibraryUiEffect.SharePdf(document.pdfPath, document.title), shareEffect.await())

        val missingEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(LibraryUiEvent.OnOpenDocumentClicked("missing-document"))
        advanceUntilIdle()
        assertEquals(LibraryUiEffect.ShowToast("Saved document not found."), missingEffect.await())
    }

    @Test
    fun deleteSelectionCanBeDismissedWithoutDeleting() = runTest {
        val document = sampleDocument()
        val repository = FakeDocumentRepository(documents = listOf(document))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id))
        assertEquals(document, viewModel.uiState.value.pendingDeleteDocument)

        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentDismissed)

        assertNull(viewModel.uiState.value.pendingDeleteDocument)
        assertTrue(repository.deletedDocumentIds.isEmpty())
    }

    @Test
    fun confirmedDeleteRemovesDocumentAndShowsToast() = runTest {
        val document = sampleDocument()
        val repository = FakeDocumentRepository(documents = listOf(document))
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id))
        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentConfirmed)
        advanceUntilIdle()

        assertEquals(listOf(document.id), repository.deletedDocumentIds)
        assertTrue(viewModel.uiState.value.documents.isEmpty())
        assertNull(viewModel.uiState.value.pendingDeleteDocument)
        assertEquals(LibraryUiEffect.ShowToast("Document deleted."), effect.await())
    }

    @Test
    fun failedDeleteShowsErrorMessageAndToast() = runTest {
        val document = sampleDocument()
        val repository = FakeDocumentRepository(
            documents = listOf(document),
            deleteResult = AppResult.Error("Storage failed.", AppErrorCategory.STORAGE)
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id))
        viewModel.onEvent(LibraryUiEvent.OnDeleteDocumentConfirmed)
        advanceUntilIdle()

        val expectedMessage = "We could not save or load this file. Please try again."
        assertEquals(expectedMessage, viewModel.uiState.value.errorMessage)
        assertEquals(LibraryUiEffect.ShowToast(expectedMessage), effect.await())
        assertNull(viewModel.uiState.value.pendingDeleteDocument)
    }

    private fun viewModel(
        documents: List<SavedDocument> = emptyList(),
        repository: FakeDocumentRepository = FakeDocumentRepository(documents = documents)
    ): LibraryViewModel = LibraryViewModel(
        observeSavedDocumentsUseCase = ObserveSavedDocumentsUseCase(repository),
        deleteSavedDocumentUseCase = DeleteSavedDocumentUseCase(repository)
    )

    private fun sampleDocument(
        id: String = "document-id",
        title: String = "Math Paper",
        metadata: DocumentMetadata = sampleMetadata(),
        createdAt: Long = 1L
    ): SavedDocument = SavedDocument(
        id = id,
        sessionId = "session-id",
        title = title,
        pdfPath = "/pdf/$id.pdf",
        thumbnailPath = "/thumb/$id.jpg",
        metadata = metadata,
        pageCount = 2,
        createdAt = createdAt
    )

    private fun sampleMetadata(
        grade: String = "Grade 10",
        subject: String = "Math",
        year: Int = 2026,
        paperType: String = "Past Paper",
        paperNumber: String? = "1"
    ): DocumentMetadata = DocumentMetadata(
        grade = grade,
        subject = subject,
        year = year,
        paperType = paperType,
        paperNumber = paperNumber
    )

    private class FakeDocumentRepository(
        documents: List<SavedDocument> = emptyList(),
        private val deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : DocumentRepository {
        private val documentsFlow = MutableStateFlow(documents)
        val deletedDocumentIds = mutableListOf<String>()

        override fun observeSavedDocuments(): Flow<List<SavedDocument>> = documentsFlow

        override suspend fun saveDocument(document: SavedDocument): AppResult<Unit> {
            documentsFlow.value = documentsFlow.value.filterNot { it.id == document.id } + document
            return AppResult.Success(Unit)
        }

        override suspend fun getDocument(documentId: String): AppResult<SavedDocument?> =
            AppResult.Success(documentsFlow.value.firstOrNull { document -> document.id == documentId })

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
            deletedDocumentIds += documentId
            if (deleteResult is AppResult.Success) {
                documentsFlow.value = documentsFlow.value.filterNot { document -> document.id == documentId }
            }
            return deleteResult
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
