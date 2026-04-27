package com.docly.app.feature.export

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FixedTimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DocumentRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.PdfRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.export.ExportDocumentUseCase
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.GeneratePdfUseCase
import com.docly.app.domain.usecase.export.PrepareExportUseCase
import com.docly.app.domain.usecase.export.SaveDocumentUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.library.DeleteSavedDocumentUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanSessionStatusUseCase
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun loadReadySessionPopulatesExportSummary() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("grade_10_math_2026_past_paper_1.pdf", state.fileName)
        assertEquals("grade_10_math_2026_past_paper_1", state.title)
        assertEquals("Grade 10 - Math - 2026 - Past Paper 1", state.metadataSummary)
        assertEquals(1, state.pageCount)
        assertTrue(state.canExport)
        assertFalse(state.hasExportedPdf)
    }

    @Test
    fun blankSessionIdShowsErrorWithoutLoadingRepository() = runTest {
        val scanRepository = FakeScanRepository()
        val viewModel = viewModel(
            scanRepository = scanRepository,
            savedStateHandle = SavedStateHandle()
        )
        advanceUntilIdle()

        assertEquals("Scan session not found.", viewModel.uiState.value.errorMessage)
        assertTrue(scanRepository.loadedSessionIds.isEmpty())
    }

    @Test
    fun exportFailureShowsErrorAndToast() = runTest {
        val viewModel = viewModel(
            pdfRepository = FakePdfRepository(
                createResult = AppResult.Error("PDF failed.", AppErrorCategory.PDF)
            )
        )
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ExportUiEvent.OnExportClicked)
        advanceUntilIdle()

        assertEquals("We could not create the PDF. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(ExportUiEffect.ShowToast("We could not create the PDF. Please try again."), effect.await())
    }

    @Test
    fun successfulExportUpdatesStateAndShowsToast() = runTest {
        val documentRepository = FakeDocumentRepository()
        val scanRepository = FakeScanRepository(session = sampleSessionWithExistingPage())
        val viewModel = viewModel(scanRepository = scanRepository, documentRepository = documentRepository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ExportUiEvent.OnExportClicked)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DOCUMENT_ID, state.exportedDocumentId)
        assertTrue(state.exportedPdfPath?.endsWith("grade_10_math_2026_past_paper_1.pdf") == true)
        assertFalse(state.canExport)
        assertEquals(documentRepository.savedDocument?.pdfPath, state.exportedPdfPath)
        assertEquals(SESSION_ID to ScanSessionStatus.EXPORTED, scanRepository.updatedStatus)
        assertEquals(ExportUiEffect.ShowToast("PDF exported."), effect.await())
    }

    @Test
    fun openAndShareBeforeExportShowSafeToasts() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val openEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnOpenPdfClicked)
        advanceUntilIdle()
        assertEquals(ExportUiEffect.ShowToast("Export the PDF before opening it."), openEffect.await())

        val shareEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnSharePdfClicked)
        advanceUntilIdle()
        assertEquals(ExportUiEffect.ShowToast("Export the PDF before sharing it."), shareEffect.await())
    }

    @Test
    fun openShareAndLibraryAfterExportEmitEffects() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val exportedToast = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnExportClicked)
        advanceUntilIdle()
        exportedToast.await()
        val exportedPath = checkNotNull(viewModel.uiState.value.exportedPdfPath)

        val openEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnOpenPdfClicked)
        advanceUntilIdle()
        assertEquals(ExportUiEffect.OpenPdf(exportedPath), openEffect.await())

        val shareEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnSharePdfClicked)
        advanceUntilIdle()
        assertEquals(
            ExportUiEffect.SharePdf(exportedPath, "grade_10_math_2026_past_paper_1"),
            shareEffect.await()
        )

        val libraryEffect = async { viewModel.uiEffect.first() }
        runCurrent()
        viewModel.onEvent(ExportUiEvent.OnOpenLibraryClicked)
        advanceUntilIdle()
        assertEquals(ExportUiEffect.NavigateToLibrary, libraryEffect.await())
    }

    private fun viewModel(
        scanRepository: FakeScanRepository = FakeScanRepository(session = sampleSessionWithExistingPage()),
        pdfRepository: FakePdfRepository = FakePdfRepository(),
        documentRepository: FakeDocumentRepository = FakeDocumentRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(temporaryFolder.root),
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(SESSION_ID_KEY to SESSION_ID))
    ): ExportViewModel {
        val prepareExportUseCase = PrepareExportUseCase(
            getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
            generateDocumentNameUseCase = GenerateDocumentNameUseCase(),
            validateMetadataUseCase = ValidateMetadataUseCase(FixedTimeProvider(FIXED_NOW))
        )
        val exportDocumentUseCase = ExportDocumentUseCase(
            prepareExportUseCase = prepareExportUseCase,
            generatePdfUseCase = GeneratePdfUseCase(pdfRepository, fileRepository),
            saveDocumentUseCase = SaveDocumentUseCase(documentRepository),
            updateScanSessionStatusUseCase = UpdateScanSessionStatusUseCase(scanRepository),
            deleteSavedDocumentUseCase = DeleteSavedDocumentUseCase(documentRepository),
            fileRepository = fileRepository,
            idProvider = FixedIdProvider(DOCUMENT_ID),
            timeProvider = FixedTimeProvider(FIXED_NOW)
        )

        return ExportViewModel(
            savedStateHandle = savedStateHandle,
            prepareExportUseCase = prepareExportUseCase,
            exportDocumentUseCase = exportDocumentUseCase
        )
    }

    private fun sampleSessionWithExistingPage(): ScanSession = ScanSession(
        id = SESSION_ID,
        createdAt = 1L,
        updatedAt = 1L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = ScanMode.DOCUMENT,
        pages = listOf(
            ScannedPage(
                id = "page-id",
                sessionId = SESSION_ID,
                pageIndex = 0,
                originalImagePath = "/raw/page-id.jpg",
                processedImagePath = existingImage("page-id.jpg"),
                thumbnailPath = "/thumb/page-id.jpg",
                rotationDegrees = 0,
                scanMode = ScanMode.DOCUMENT,
                width = 100,
                height = 200,
                createdAt = 1L
            )
        ),
        metadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Math",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = "1"
        )
    )

    private fun existingImage(name: String): String {
        val file = File(temporaryFolder.root, "processed/$name")
        file.parentFile?.mkdirs()
        file.writeText("image")
        return file.absolutePath
    }

    private class FakeScanRepository(
        private val session: ScanSession? = null,
        private val statusResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        val loadedSessionIds = mutableListOf<String>()
        var updatedStatus: Pair<String, ScanSessionStatus>? = null

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> {
            loadedSessionIds += sessionId
            return AppResult.Success(session)
        }

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            error("Not used.")

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun deletePage(pageId: String): AppResult<Unit> = error("Not used.")

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            error("Not used.")

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            when (statusResult) {
                is AppResult.Error -> statusResult

                is AppResult.Success -> {
                    updatedStatus = sessionId to status
                    AppResult.Success(Unit)
                }
            }
    }

    private class FakePdfRepository(private val createResult: AppResult<String>? = null) : PdfRepository {
        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
            val configuredResult = createResult
            if (configuredResult != null) return configuredResult

            File(outputPdfPath).apply {
                parentFile?.mkdirs()
                writeText("pdf")
            }
            return AppResult.Success(outputPdfPath)
        }
    }

    private class FakeDocumentRepository : DocumentRepository {
        var savedDocument: SavedDocument? = null

        override fun observeSavedDocuments(): Flow<List<SavedDocument>> = flowOf(emptyList())

        override suspend fun saveDocument(document: SavedDocument): AppResult<Unit> {
            savedDocument = document
            return AppResult.Success(Unit)
        }

        override suspend fun getDocument(documentId: String): AppResult<SavedDocument?> = AppResult.Success(null)

        override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
            savedDocument = null
            return AppResult.Success(Unit)
        }
    }

    private class FakeFileRepository(private val root: File) : FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = File(root, "pdf/$fileName").absolutePath

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> {
            File(path).delete()
            return AppResult.Success(Unit)
        }

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FixedIdProvider(private val id: String) : IdProvider {
        override fun generateId(): String = id
    }

    private companion object {
        const val SESSION_ID = "session-id"
        const val DOCUMENT_ID = "document-id"
        const val SESSION_ID_KEY = "sessionId"
        val FIXED_NOW: Long = GregorianCalendar(2026, Calendar.JANUARY, 1).timeInMillis
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
