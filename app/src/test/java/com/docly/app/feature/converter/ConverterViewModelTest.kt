package com.docly.app.feature.converter

import com.docly.app.core.reader.WorkbookDocument
import com.docly.app.core.reader.XlsxReaderEngine
import com.docly.app.core.reader.XlsxRowPage
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeDocumentRepository
import com.docly.app.domain.model.ConversionJob
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.ConversionResult
import com.docly.app.domain.model.ConversionStatus
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.ConverterRepository
import com.docly.app.domain.usecase.converter.ConvertDocumentUseCase
import com.docly.app.domain.usecase.converter.GetSupportedConversionOutputsUseCase
import com.docly.app.domain.usecase.library.ObserveDocumentsUseCase
import com.docly.app.domain.usecase.reader.OpenXlsxUseCase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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
class ConverterViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startLoadsConvertibleDocumentsAndDefaultOutput() = runTest(mainDispatcherRule.testDispatcher) {
        val converterRepository = FakeConverterRepository()
        val viewModel = viewModel(
            documents = listOf(
                document(id = "txt", type = DocumentType.TXT),
                document(id = "pdf", type = DocumentType.PDF)
            ),
            converterRepository = converterRepository
        )

        viewModel.onEvent(ConverterUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals(listOf("txt"), viewModel.uiState.value.documents.map { document -> document.id })
        assertEquals("txt", viewModel.uiState.value.selectedDocumentId)
        assertEquals(DocumentType.PDF, viewModel.uiState.value.selectedOutputType)
        assertEquals("Notes.pdf", viewModel.uiState.value.outputFileName)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun selectingXlsxLoadsSheetOptions() = runTest(mainDispatcherRule.testDispatcher) {
        val converterRepository = FakeConverterRepository(
            supportedOutputs = mapOf(DocumentType.XLSX to listOf(DocumentType.CSV, DocumentType.TXT))
        )
        val viewModel = viewModel(
            documents = listOf(document(id = "xlsx", type = DocumentType.XLSX)),
            converterRepository = converterRepository
        )

        viewModel.onEvent(ConverterUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals(
            listOf(XlsxSheetInfo("Sheet One", 0), XlsxSheetInfo("Sheet Two", 1)),
            viewModel.uiState.value.xlsxSheets
        )
        assertEquals(0, viewModel.uiState.value.selectedXlsxSheetIndex)
    }

    @Test
    fun convertSuccessStoresResultAndOpenShareEffectsUseResult() = runTest(mainDispatcherRule.testDispatcher) {
        val converterRepository = FakeConverterRepository()
        val viewModel = viewModel(
            documents = listOf(document(id = "txt", type = DocumentType.TXT)),
            converterRepository = converterRepository
        )
        val effects = async { viewModel.uiEffect.take(3).toList() }

        viewModel.onEvent(ConverterUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ConverterUiEvent.OnConvertClicked)
        advanceUntilIdle()
        viewModel.onEvent(ConverterUiEvent.OnOpenResultClicked)
        viewModel.onEvent(ConverterUiEvent.OnShareResultClicked)
        advanceUntilIdle()

        assertEquals("output-id", viewModel.uiState.value.completedDocumentId)
        assertEquals(100, viewModel.uiState.value.progress)
        assertEquals(DocumentType.PDF, converterRepository.lastRequest?.outputType)
        val emittedEffects = effects.await()
        assertEquals(ConverterUiEffect.ShowToast("Document converted."), emittedEffects[0])
        assertEquals(ConverterUiEffect.NavigateToReader("output-id"), emittedEffects[1])
        assertEquals("/converted/output.pdf", (emittedEffects[2] as ConverterUiEffect.ShareDocument).filePath)
    }

    private fun viewModel(
        documents: List<DoclyDocument>,
        converterRepository: FakeConverterRepository
    ): ConverterViewModel = ConverterViewModel(
        observeDocumentsUseCase = ObserveDocumentsUseCase(FakeDocumentRepository(documents)),
        getSupportedConversionOutputsUseCase = GetSupportedConversionOutputsUseCase(converterRepository),
        convertDocumentUseCase = ConvertDocumentUseCase(converterRepository),
        openXlsxUseCase = OpenXlsxUseCase(FakeXlsxReaderEngine())
    )

    private fun document(id: String, type: DocumentType): DoclyDocument {
        val file = temporaryFolder.newFile("$id.${type.name.lowercase()}").apply {
            writeText("fixture")
        }
        return DoclyDocument(
            id = id,
            name = "Notes",
            type = type,
            mimeType = null,
            fileRef = FileRef.InternalFile(file.absolutePath),
            source = DocumentSource.CREATED,
            fileSize = File(file.absolutePath).length(),
            createdAt = 1L,
            updatedAt = 1L
        )
    }

    private class FakeConverterRepository(
        private val supportedOutputs: Map<DocumentType, List<DocumentType>> = mapOf(
            DocumentType.TXT to listOf(DocumentType.PDF, DocumentType.HTML)
        )
    ) : ConverterRepository {
        var lastRequest: ConversionRequest? = null

        override fun getSupportedOutputs(inputType: DocumentType): List<DocumentType> =
            supportedOutputs[inputType].orEmpty()

        override suspend fun createJob(request: ConversionRequest): AppResult<ConversionJob> = AppResult.Success(job())

        override fun observeJob(jobId: String): Flow<ConversionJob?> = flowOf(job())

        override fun observeRecentJobs(limit: Int): Flow<List<ConversionJob>> = flowOf(listOf(job()))

        override suspend fun convert(request: ConversionRequest): AppResult<ConversionResult> {
            lastRequest = request
            val outputDocument = DoclyDocument(
                id = "output-id",
                name = "Output",
                type = request.outputType,
                mimeType = "application/pdf",
                fileRef = FileRef.InternalFile("/converted/output.pdf"),
                source = DocumentSource.CONVERTED,
                fileSize = 10L,
                createdAt = 1L,
                updatedAt = 1L
            )
            return AppResult.Success(
                ConversionResult(
                    job = job(status = ConversionStatus.COMPLETED, progress = 100),
                    outputDocument = outputDocument,
                    outputPath = "/converted/output.pdf"
                )
            )
        }

        private fun job(status: ConversionStatus = ConversionStatus.QUEUED, progress: Int = 0): ConversionJob =
            ConversionJob(
                id = "job-id",
                inputDocumentId = "txt",
                inputUri = null,
                inputType = DocumentType.TXT,
                outputType = DocumentType.PDF,
                outputPath = null,
                outputDocumentId = null,
                status = status,
                progress = progress,
                errorMessage = null,
                createdAt = 1L,
                updatedAt = 1L
            )
    }

    private class FakeXlsxReaderEngine : XlsxReaderEngine {
        override suspend fun open(fileRef: FileRef): AppResult<WorkbookDocument> = AppResult.Success(
            WorkbookDocument(
                listOf(
                    XlsxSheetInfo("Sheet One", 0),
                    XlsxSheetInfo("Sheet Two", 1)
                )
            )
        )

        override suspend fun readRows(
            fileRef: FileRef,
            sheetIndex: Int,
            startRowIndex: Int,
            maxRows: Int
        ): AppResult<XlsxRowPage> = AppResult.Success(XlsxRowPage(emptyList(), null, hasMore = false))
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
