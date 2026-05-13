package com.docly.app.data.repository

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeDoclyStorageManager
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.core.testing.SequenceIdProvider
import com.docly.app.core.testing.TestDispatcherProvider
import com.docly.app.data.local.dao.ConversionJobDao
import com.docly.app.data.local.entity.ConversionJobEntity
import com.docly.app.data.local.mapper.toDomain
import com.docly.app.domain.converter.ConverterEngine
import com.docly.app.domain.converter.ConverterOutput
import com.docly.app.domain.converter.ConverterRegistry
import com.docly.app.domain.model.ConversionPair
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.ConversionStatus
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterRepositoryImplTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher)

    @Test
    fun convertPersistsLifecycleAndRegistersConvertedDocument() = runTest(dispatcher) {
        val sourceFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Notes", Charsets.UTF_8)
        }
        val sourceDocument = document(path = sourceFile.absolutePath)
        val documentRepository = FakeDocumentRepository(listOf(sourceDocument))
        val conversionJobDao = FakeConversionJobDao()
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val repository = repository(
            conversionJobDao = conversionJobDao,
            documentRepository = documentRepository,
            storageManager = storageManager,
            engine = FakeConverterEngine()
        )

        val result = repository.convert(request())

        assertTrue(result is AppResult.Success)
        val conversionResult = (result as AppResult.Success).data
        assertEquals("output-doc-id", conversionResult.outputDocument.id)
        assertEquals(DocumentSource.CONVERTED, conversionResult.outputDocument.source)
        assertEquals(DocumentType.HTML, conversionResult.outputDocument.type)
        assertTrue(File(conversionResult.outputPath).isFile)
        assertTrue(documentRepository.documents.any { document -> document.id == "output-doc-id" })
        assertEquals(
            listOf(ConversionStatus.QUEUED, ConversionStatus.RUNNING, ConversionStatus.COMPLETED),
            conversionJobDao.history.map { entity -> entity.toDomain().status }
        )
        assertEquals("output-doc-id", conversionJobDao.history.last().outputDocumentId)
    }

    @Test
    fun convertDeletesPartialOutputAndPersistsFailureWhenEngineFails() = runTest(dispatcher) {
        val sourceFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Notes", Charsets.UTF_8)
        }
        val sourceDocument = document(path = sourceFile.absolutePath)
        val documentRepository = FakeDocumentRepository(listOf(sourceDocument))
        val conversionJobDao = FakeConversionJobDao()
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val repository = repository(
            conversionJobDao = conversionJobDao,
            documentRepository = documentRepository,
            storageManager = storageManager,
            engine = FakeConverterEngine(
                result = AppResult.Error("Conversion failed.", AppErrorCategory.PROCESSING)
            )
        )

        val result = repository.convert(request())

        assertTrue(result is AppResult.Error)
        val outputPath = storageManager.createdPaths.single()
        assertFalse(File(outputPath).exists())
        assertEquals(listOf(outputPath), storageManager.deletedPaths)
        assertEquals(ConversionStatus.FAILED, conversionJobDao.history.last().toDomain().status)
        assertEquals("Conversion failed.", conversionJobDao.history.last().errorMessage)
        assertEquals(listOf(sourceDocument), documentRepository.documents)
    }

    @Test
    fun convertRejectsUnsupportedPairsBeforeWritingOutput() = runTest(dispatcher) {
        val sourceFile = temporaryFolder.newFile("notes.txt").apply {
            writeText("Notes", Charsets.UTF_8)
        }
        val sourceDocument = document(path = sourceFile.absolutePath)
        val conversionJobDao = FakeConversionJobDao()
        val storageManager = FakeDoclyStorageManager(temporaryFolder.root)
        val repository = repository(
            conversionJobDao = conversionJobDao,
            documentRepository = FakeDocumentRepository(listOf(sourceDocument)),
            storageManager = storageManager,
            engine = FakeConverterEngine()
        )

        val result = repository.convert(request(outputType = DocumentType.CSV))

        assertTrue(result is AppResult.Error)
        assertTrue(storageManager.createdPaths.isEmpty())
        assertEquals(ConversionStatus.FAILED, conversionJobDao.history.last().toDomain().status)
    }

    private fun repository(
        conversionJobDao: FakeConversionJobDao,
        documentRepository: FakeDocumentRepository,
        storageManager: FakeDoclyStorageManager,
        engine: ConverterEngine
    ): ConverterRepositoryImpl = ConverterRepositoryImpl(
        conversionJobDao = conversionJobDao,
        documentRepository = documentRepository,
        storageManager = storageManager,
        converterRegistry = ConverterRegistry(setOf(engine)),
        idProvider = SequenceIdProvider(listOf("job-id", "output-doc-id")),
        timeProvider = FixedTimeProvider(100L),
        dispatcherProvider = dispatcherProvider
    )

    private fun request(outputType: DocumentType = DocumentType.HTML): ConversionRequest = ConversionRequest(
        inputDocumentId = "source-id",
        inputType = DocumentType.TXT,
        outputType = outputType,
        outputFileName = "notes.html"
    )

    private fun document(path: String): DoclyDocument = DoclyDocument(
        id = "source-id",
        name = "Notes",
        type = DocumentType.TXT,
        mimeType = "text/plain",
        fileRef = FileRef.InternalFile(path),
        source = DocumentSource.CREATED,
        fileSize = File(path).length(),
        createdAt = 1L,
        updatedAt = 1L
    )

    private class FakeConverterEngine(private val result: AppResult<ConverterOutput>? = null) : ConverterEngine {
        override val supportedPairs: Set<ConversionPair> = setOf(
            ConversionPair(DocumentType.TXT, DocumentType.HTML)
        )

        override suspend fun convert(
            request: ConversionRequest,
            sourceDocument: DoclyDocument,
            outputPath: String
        ): AppResult<ConverterOutput> {
            File(outputPath).writeText("<html></html>", Charsets.UTF_8)
            return result ?: AppResult.Success(ConverterOutput(outputPath = outputPath, mimeType = "text/html"))
        }
    }

    private class FakeConversionJobDao : ConversionJobDao {
        private val jobs = MutableStateFlow<List<ConversionJobEntity>>(emptyList())
        val history = mutableListOf<ConversionJobEntity>()

        override suspend fun upsert(job: ConversionJobEntity) {
            history += job
            jobs.value = jobs.value.filterNot { existing -> existing.id == job.id } + job
        }

        override fun observeJob(jobId: String): Flow<ConversionJobEntity?> =
            jobs.map { entities -> entities.firstOrNull { entity -> entity.id == jobId } }

        override fun observeRecent(limit: Int): Flow<List<ConversionJobEntity>> =
            jobs.map { entities -> entities.sortedByDescending { entity -> entity.updatedAt }.take(limit) }

        override suspend fun update(job: ConversionJobEntity) {
            upsert(job)
        }
    }
}
