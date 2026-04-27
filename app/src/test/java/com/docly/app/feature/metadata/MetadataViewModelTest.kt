package com.docly.app.feature.metadata

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.export.GenerateDocumentNameUseCase
import com.docly.app.domain.usecase.export.ValidateMetadataUseCase
import com.docly.app.domain.usecase.session.GetScanSessionUseCase
import com.docly.app.domain.usecase.session.UpdateScanMetadataUseCase
import java.util.Calendar
import java.util.GregorianCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadExistingMetadataPopulatesFormAndFilenamePreview() = runTest {
        val repository = FakeScanRepository(
            session = sampleSession(
                metadata = DocumentMetadata(
                    grade = "Grade 10",
                    subject = "Math / Stats",
                    year = 2026,
                    paperType = "Paper 1",
                    paperNumber = "A&B",
                    source = "Archive",
                    notes = "Clean copy"
                )
            )
        )
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Grade 10", state.grade)
        assertEquals("Math / Stats", state.subject)
        assertEquals("2026", state.year)
        assertEquals("Paper 1", state.paperType)
        assertEquals("A&B", state.paperNumber)
        assertEquals("Archive", state.source)
        assertEquals("Clean copy", state.notes)
        assertEquals("grade_10_math_stats_2026_paper_1_a_b.pdf", state.generatedFileName)
    }

    @Test
    fun blankSessionIdShowsSessionErrorWithoutRepositoryLoad() = runTest {
        val repository = FakeScanRepository()
        val viewModel = viewModel(
            scanRepository = repository,
            savedStateHandle = SavedStateHandle()
        )
        advanceUntilIdle()

        assertEquals("Scan session not found.", viewModel.uiState.value.errorMessage)
        assertTrue(repository.loadedSessionIds.isEmpty())
    }

    @Test
    fun fieldChangesUpdateFilenamePreview() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEvent(MetadataUiEvent.OnGradeChanged("Grade 10"))
        viewModel.onEvent(MetadataUiEvent.OnSubjectChanged("Math / Stats"))
        viewModel.onEvent(MetadataUiEvent.OnYearChanged("2026"))
        viewModel.onEvent(MetadataUiEvent.OnPaperTypeChanged("Paper 1"))
        viewModel.onEvent(MetadataUiEvent.OnPaperNumberChanged("A&B"))

        assertEquals("grade_10_math_stats_2026_paper_1_a_b.pdf", viewModel.uiState.value.generatedFileName)
    }

    @Test
    fun requiredFieldValidationBlocksSave() = runTest {
        val repository = FakeScanRepository()
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()

        viewModel.onEvent(MetadataUiEvent.OnYearChanged("2026"))
        viewModel.onEvent(MetadataUiEvent.OnContinueClicked)

        assertEquals(
            listOf("Grade is required.", "Subject is required.", "Paper type is required."),
            viewModel.uiState.value.validationErrors
        )
        assertNull(repository.updatedMetadata)
    }

    @Test
    fun nonIntegerYearValidationBlocksSave() = runTest {
        val repository = FakeScanRepository()
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()

        viewModel.onEvent(MetadataUiEvent.OnGradeChanged("Grade 10"))
        viewModel.onEvent(MetadataUiEvent.OnSubjectChanged("Mathematics"))
        viewModel.onEvent(MetadataUiEvent.OnYearChanged("20x6"))
        viewModel.onEvent(MetadataUiEvent.OnPaperTypeChanged("Past Paper"))
        viewModel.onEvent(MetadataUiEvent.OnContinueClicked)

        assertEquals(listOf("Year must be a whole number."), viewModel.uiState.value.validationErrors)
        assertNull(repository.updatedMetadata)
    }

    @Test
    fun outOfRangeYearValidationBlocksSave() = runTest {
        val repository = FakeScanRepository()
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()

        viewModel.onEvent(MetadataUiEvent.OnGradeChanged("Grade 10"))
        viewModel.onEvent(MetadataUiEvent.OnSubjectChanged("Mathematics"))
        viewModel.onEvent(MetadataUiEvent.OnYearChanged("1979"))
        viewModel.onEvent(MetadataUiEvent.OnPaperTypeChanged("Past Paper"))
        viewModel.onEvent(MetadataUiEvent.OnContinueClicked)

        assertEquals(listOf("Year must be between 1980 and 2027."), viewModel.uiState.value.validationErrors)
        assertNull(repository.updatedMetadata)
    }

    @Test
    fun successfulSaveTrimsValuesPersistsMetadataAndNavigates() = runTest {
        val repository = FakeScanRepository()
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(MetadataUiEvent.OnGradeChanged(" Grade 10 "))
        viewModel.onEvent(MetadataUiEvent.OnSubjectChanged(" Mathematics "))
        viewModel.onEvent(MetadataUiEvent.OnYearChanged(" 2026 "))
        viewModel.onEvent(MetadataUiEvent.OnPaperTypeChanged(" Past Paper "))
        viewModel.onEvent(MetadataUiEvent.OnPaperNumberChanged(" "))
        viewModel.onEvent(MetadataUiEvent.OnSourceChanged(" Archive "))
        viewModel.onEvent(MetadataUiEvent.OnNotesChanged(""))
        viewModel.onEvent(MetadataUiEvent.OnContinueClicked)
        advanceUntilIdle()

        val expectedMetadata = DocumentMetadata(
            grade = "Grade 10",
            subject = "Mathematics",
            year = 2026,
            paperType = "Past Paper",
            paperNumber = null,
            source = "Archive",
            notes = null
        )
        assertEquals(SESSION_ID to expectedMetadata, repository.updatedMetadata)
        assertEquals(MetadataUiEffect.NavigateToExport(SESSION_ID), effect.await())
        assertEquals("Grade 10", viewModel.uiState.value.grade)
        assertEquals("", viewModel.uiState.value.paperNumber)
    }

    @Test
    fun saveFailureShowsInlineErrorAndToast() = runTest {
        val repository = FakeScanRepository(
            updateResult = AppResult.Error(
                message = "Metadata write failed.",
                category = AppErrorCategory.VALIDATION
            )
        )
        val viewModel = viewModel(scanRepository = repository)
        advanceUntilIdle()
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(MetadataUiEvent.OnGradeChanged("Grade 10"))
        viewModel.onEvent(MetadataUiEvent.OnSubjectChanged("Mathematics"))
        viewModel.onEvent(MetadataUiEvent.OnYearChanged("2026"))
        viewModel.onEvent(MetadataUiEvent.OnPaperTypeChanged("Past Paper"))
        viewModel.onEvent(MetadataUiEvent.OnContinueClicked)
        advanceUntilIdle()

        assertEquals("Metadata write failed.", viewModel.uiState.value.errorMessage)
        assertEquals(MetadataUiEffect.ShowToast("Metadata write failed."), effect.await())
    }

    private fun viewModel(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(mapOf(SESSION_ID_KEY to SESSION_ID))
    ): MetadataViewModel = MetadataViewModel(
        savedStateHandle = savedStateHandle,
        getScanSessionUseCase = GetScanSessionUseCase(scanRepository),
        updateScanMetadataUseCase = UpdateScanMetadataUseCase(scanRepository),
        generateDocumentNameUseCase = GenerateDocumentNameUseCase(),
        validateMetadataUseCase = ValidateMetadataUseCase(fixedTimeProvider)
    )

    private class FakeScanRepository(
        private var session: ScanSession? = sampleSession(),
        private val updateResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        val loadedSessionIds: MutableList<String> = mutableListOf()
        var updatedMetadata: Pair<String, DocumentMetadata>? = null

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = error("Not used.")

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> {
            loadedSessionIds += sessionId
            return AppResult.Success(session)
        }

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = error("Not used.")

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            when (updateResult) {
                is AppResult.Error -> updateResult

                is AppResult.Success -> {
                    updatedMetadata = sessionId to metadata
                    session = session?.copy(metadata = metadata)
                    AppResult.Success(Unit)
                }
            }

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = error("Not used.")

        override suspend fun deletePage(pageId: String): AppResult<Unit> = error("Not used.")

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            error("Not used.")

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            error("Not used.")
    }

    private companion object {
        const val SESSION_ID = "session-id"
        const val SESSION_ID_KEY = "sessionId"

        val fixedTimeProvider = object : TimeProvider {
            override fun now(): Long = GregorianCalendar(2026, Calendar.JANUARY, 1).timeInMillis
        }

        fun sampleSession(metadata: DocumentMetadata? = null): ScanSession = ScanSession(
            id = SESSION_ID,
            createdAt = 1L,
            updatedAt = 1L,
            status = ScanSessionStatus.IN_PROGRESS,
            scanMode = ScanMode.DOCUMENT,
            metadata = metadata
        )
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
