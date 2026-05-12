package com.docly.app.feature.documenteditor

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.RenderedHtmlDocument
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeDoclyStorageManager
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.core.testing.FakeHtmlToPdfExporter
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.core.testing.SequenceIdProvider
import com.docly.app.core.testing.TestDispatcherProvider
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.usecase.create.CreatePdfFromTextDocumentUseCase
import com.docly.app.domain.usecase.create.LoadEditableDocumentUseCase
import com.docly.app.domain.usecase.create.RenderEditablePreviewUseCase
import com.docly.app.domain.usecase.create.SaveEditableDocumentUseCase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentEditorViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadEditAndSaveUpdatesDocumentContent() = runTest(mainDispatcherRule.testDispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Original", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        )
        val viewModel = viewModel(repository = repository)

        viewModel.onEvent(DocumentEditorUiEvent.OnStart)
        advanceUntilIdle()
        assertEquals("Original", viewModel.uiState.value.content)

        val effect = async { viewModel.uiEffect.first() }
        viewModel.onEvent(DocumentEditorUiEvent.OnContentChanged("Updated"))
        viewModel.onEvent(DocumentEditorUiEvent.OnSaveClicked)
        advanceUntilIdle()

        assertEquals("Updated", textFile.readText(Charsets.UTF_8))
        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals(DocumentEditorUiEffect.ShowToast("Document saved."), effect.await())
    }

    @Test
    fun autosavePersistsContentAfterDelayWithoutToast() = runTest(mainDispatcherRule.testDispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Original", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        )
        val viewModel = viewModel(repository = repository)
        viewModel.onEvent(DocumentEditorUiEvent.OnStart)
        advanceUntilIdle()

        viewModel.onEvent(DocumentEditorUiEvent.OnContentChanged("Autosaved"))
        advanceTimeBy(1499L)
        assertEquals("Original", textFile.readText(Charsets.UTF_8))

        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals("Autosaved", textFile.readText(Charsets.UTF_8))
        assertFalse(viewModel.uiState.value.isDirty)
        assertEquals(DocumentEditorSaveStatus.SAVED, viewModel.uiState.value.saveStatus)
    }

    @Test
    fun searchCountsAndCyclesMatches() = runTest(mainDispatcherRule.testDispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Alpha beta alpha", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        )
        val viewModel = viewModel(repository = repository)
        viewModel.onEvent(DocumentEditorUiEvent.OnStart)
        advanceUntilIdle()

        viewModel.onEvent(DocumentEditorUiEvent.OnSearchQueryChanged("alpha"))

        assertEquals(2, viewModel.uiState.value.searchResultCount)
        assertEquals("1 of 2", viewModel.uiState.value.searchSummary)

        viewModel.onEvent(DocumentEditorUiEvent.OnNextSearchResultClicked)
        assertEquals("2 of 2", viewModel.uiState.value.searchSummary)

        viewModel.onEvent(DocumentEditorUiEvent.OnNextSearchResultClicked)
        assertEquals("1 of 2", viewModel.uiState.value.searchSummary)
    }

    @Test
    fun markdownPreviewRendersUnsavedSource() = runTest(mainDispatcherRule.testDispatcher) {
        val markdownFile = temporaryFolder.newFile("notes.md").apply {
            writeText("# Original", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = markdownFile.absolutePath, type = DocumentType.MARKDOWN))
        )
        val viewModel = viewModel(repository = repository)
        viewModel.onEvent(DocumentEditorUiEvent.OnStart)
        advanceUntilIdle()

        viewModel.onEvent(DocumentEditorUiEvent.OnContentChanged("# Preview"))
        viewModel.onEvent(DocumentEditorUiEvent.OnEditorModeChanged(DocumentEditorMode.PREVIEW))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.previewHtml.contains("<h1>Preview</h1>"))
        assertEquals(DocumentEditorMode.PREVIEW, viewModel.uiState.value.editorMode)
    }

    @Test
    fun exportPdfSavesDirtyContentAndNavigatesToReader() = runTest(mainDispatcherRule.testDispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Original", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        )
        val viewModel = viewModel(repository = repository)
        viewModel.onEvent(DocumentEditorUiEvent.OnStart)
        advanceUntilIdle()

        viewModel.onEvent(DocumentEditorUiEvent.OnContentChanged("Unsaved"))
        val exportEffects = async { viewModel.uiEffect.take(2).toList() }
        viewModel.onEvent(DocumentEditorUiEvent.OnExportPdfClicked)
        advanceUntilIdle()

        assertEquals("Unsaved", textFile.readText(Charsets.UTF_8))
        val emittedEffects = exportEffects.await()
        assertEquals(DocumentEditorUiEffect.ShowToast("PDF created."), emittedEffects.first())
        assertEquals(DocumentEditorUiEffect.NavigateToReader("pdf-id"), emittedEffects.last())
        assertTrue(repository.documents.any { document -> document.id == "pdf-id" })
    }

    private fun viewModel(repository: FakeDocumentRepository): DocumentEditorViewModel {
        val dispatcherProvider = TestDispatcherProvider(mainDispatcherRule.testDispatcher)
        return DocumentEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to "notes")),
            loadEditableDocumentUseCase = LoadEditableDocumentUseCase(repository, dispatcherProvider),
            saveEditableDocumentUseCase = SaveEditableDocumentUseCase(
                documentRepository = repository,
                timeProvider = FixedTimeProvider(200L),
                dispatcherProvider = dispatcherProvider
            ),
            createPdfFromTextDocumentUseCase = CreatePdfFromTextDocumentUseCase(
                documentRepository = repository,
                storageManager = FakeDoclyStorageManager(temporaryFolder.root),
                markdownReaderEngine = FakeMarkdownReaderEngine,
                htmlToPdfExporter = FakeHtmlToPdfExporter(),
                idProvider = SequenceIdProvider(listOf("pdf-id")),
                timeProvider = FixedTimeProvider(300L),
                dispatcherProvider = dispatcherProvider
            ),
            renderEditablePreviewUseCase = RenderEditablePreviewUseCase(dispatcherProvider)
        )
    }

    private fun document(id: String, path: String, type: DocumentType): DoclyDocument = DoclyDocument(
        id = id,
        name = "Notes",
        type = type,
        mimeType = "text/plain",
        fileRef = FileRef.InternalFile(path),
        source = DocumentSource.CREATED,
        fileSize = File(path).length(),
        createdAt = 100L,
        updatedAt = 100L
    )

    private object FakeMarkdownReaderEngine : MarkdownReaderEngine {
        override suspend fun render(fileRef: FileRef): AppResult<RenderedHtmlDocument> =
            AppResult.Success(RenderedHtmlDocument("<html><body>Markdown</body></html>"))
    }

    class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
