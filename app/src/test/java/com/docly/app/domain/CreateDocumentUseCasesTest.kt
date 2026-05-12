package com.docly.app.domain

import com.docly.app.core.reader.MarkdownReaderEngine
import com.docly.app.core.reader.RenderedHtmlDocument
import com.docly.app.core.result.AppErrorCategory
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
import com.docly.app.domain.usecase.create.CreateDocumentUseCase
import com.docly.app.domain.usecase.create.CreatePdfFromTextDocumentUseCase
import com.docly.app.domain.usecase.create.DefaultDocumentContentFactory
import com.docly.app.domain.usecase.create.LoadEditableDocumentUseCase
import com.docly.app.domain.usecase.create.SaveEditableDocumentUseCase
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDocumentUseCasesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher)

    @Test
    fun defaultContentFactoryCreatesExpectedEditableSeeds() {
        val factory = DefaultDocumentContentFactory()

        assertEquals("", factory.create(DocumentType.TXT, "Notes"))
        assertEquals("# Notes\n\n", factory.create(DocumentType.MARKDOWN, "Notes"))
        assertTrue(factory.create(DocumentType.HTML, "Notes").contains("<title>Notes</title>"))
    }

    @Test
    fun createDocumentWritesDefaultContentAndRegistersCreatedDocument() = runTest(dispatcher) {
        val repository = FakeDocumentRepository()
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val useCase = createDocumentUseCase(repository = repository, storageManager = storageManager)

        val result = useCase(title = "Notes.md", type = DocumentType.MARKDOWN)

        assertTrue(result is AppResult.Success)
        val document = (result as AppResult.Success).data
        val path = (document.fileRef as FileRef.InternalFile).path
        assertEquals("document-id", document.id)
        assertEquals("Notes", document.name)
        assertEquals(DocumentType.MARKDOWN, document.type)
        assertEquals(DocumentSource.CREATED, document.source)
        assertTrue(path.endsWith(".md"))
        assertEquals("# Notes\n\n", File(path).readText())
        assertEquals(listOf(document), repository.documents)
    }

    @Test
    fun createDocumentDeletesFileWhenRegistrationFails() = runTest(dispatcher) {
        val repository = FakeDocumentRepository().apply {
            upsertError = AppResult.Error("Insert failed.", AppErrorCategory.STORAGE)
        }
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val useCase = createDocumentUseCase(repository = repository, storageManager = storageManager)

        val result = useCase(title = "Notes", type = DocumentType.TXT)

        assertTrue(result is AppResult.Error)
        val createdPath = storageManager.createdPaths.single()
        assertFalse(File(createdPath).exists())
        assertEquals(listOf(createdPath), storageManager.deletedPaths)
    }

    @Test
    fun loadAndSaveEditableDocumentReadAndUpdateInternalTextFile() = runTest(dispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Original", Charsets.UTF_8)
        }
        val document = document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT)
        val repository = FakeDocumentRepository(listOf(document))
        val loadUseCase = LoadEditableDocumentUseCase(repository, dispatcherProvider)
        val saveUseCase = SaveEditableDocumentUseCase(
            documentRepository = repository,
            timeProvider = FixedTimeProvider(200L),
            dispatcherProvider = dispatcherProvider
        )

        val loadResult = loadUseCase("notes")
        assertTrue(loadResult is AppResult.Success)
        assertEquals("Original", (loadResult as AppResult.Success).data.content)

        val saveResult = saveUseCase(documentId = "notes", content = "Updated")

        assertTrue(saveResult is AppResult.Success)
        assertEquals("Updated", textFile.readText(Charsets.UTF_8))
        assertEquals(200L, (saveResult as AppResult.Success).data.updatedAt)
        assertEquals("Updated".length.toLong(), repository.documents.single().fileSize)
    }

    @Test
    fun createPdfFromTextDocumentRendersEscapedHtmlAndRegistersPdf() = runTest(dispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("A < B", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        )
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val exporter = FakeHtmlToPdfExporter()
        val useCase = createPdfUseCase(repository = repository, storageManager = storageManager, exporter = exporter)

        val result = useCase("notes")

        assertTrue(result is AppResult.Success)
        val pdfDocument = (result as AppResult.Success).data
        assertEquals(DocumentType.PDF, pdfDocument.type)
        assertEquals(DocumentSource.CREATED, pdfDocument.source)
        assertTrue((pdfDocument.fileRef as FileRef.InternalFile).path.endsWith(".pdf"))
        assertTrue(exporter.lastHtml.orEmpty().contains("A &lt; B"))
        assertNotNull(repository.documents.firstOrNull { document -> document.id == "pdf-id" })
    }

    @Test
    fun createPdfFromTextDocumentDeletesPdfWhenRegistrationFails() = runTest(dispatcher) {
        val textFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Notes", Charsets.UTF_8)
        }
        val repository = FakeDocumentRepository(
            listOf(document(id = "notes", path = textFile.absolutePath, type = DocumentType.TXT))
        ).apply {
            upsertError = AppResult.Error("Insert failed.", AppErrorCategory.STORAGE)
        }
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val useCase = createPdfUseCase(
            repository = repository,
            storageManager = storageManager,
            exporter = FakeHtmlToPdfExporter()
        )

        val result = useCase("notes")

        assertTrue(result is AppResult.Error)
        val outputPath = storageManager.createdPaths.single()
        assertFalse(File(outputPath).exists())
        assertEquals(listOf(outputPath), storageManager.deletedPaths)
    }

    private fun createDocumentUseCase(
        repository: FakeDocumentRepository,
        storageManager: FakeDoclyStorageManager
    ): CreateDocumentUseCase = CreateDocumentUseCase(
        storageManager = storageManager,
        documentRepository = repository,
        defaultDocumentContentFactory = DefaultDocumentContentFactory(),
        idProvider = SequenceIdProvider(listOf("document-id")),
        timeProvider = FixedTimeProvider(100L),
        dispatcherProvider = dispatcherProvider
    )

    private fun createPdfUseCase(
        repository: FakeDocumentRepository,
        storageManager: FakeDoclyStorageManager,
        exporter: FakeHtmlToPdfExporter
    ): CreatePdfFromTextDocumentUseCase = CreatePdfFromTextDocumentUseCase(
        documentRepository = repository,
        storageManager = storageManager,
        markdownReaderEngine = FakeMarkdownReaderEngine,
        htmlToPdfExporter = exporter,
        idProvider = SequenceIdProvider(listOf("pdf-id")),
        timeProvider = FixedTimeProvider(300L),
        dispatcherProvider = dispatcherProvider
    )

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
}
