package com.docly.app.feature.scanner

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.page.ImportDevicePhotosUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun permissionResultsUpdateCameraPermissionStatus() {
        val viewModel = ScannerViewModel(importDevicePhotosUseCase = importUseCase())

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))

        assertEquals(CameraPermissionStatus.Granted, viewModel.uiState.value.cameraPermissionStatus)
        assertTrue(viewModel.uiState.value.isCameraPermissionGranted)
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Denied))

        assertEquals(CameraPermissionStatus.Denied, viewModel.uiState.value.cameraPermissionStatus)
        assertFalse(viewModel.uiState.value.isCameraPermissionGranted)

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.PermanentlyDenied))

        assertEquals(CameraPermissionStatus.PermanentlyDenied, viewModel.uiState.value.cameraPermissionStatus)
        assertFalse(viewModel.uiState.value.isCameraPermissionGranted)
    }

    @Test
    fun cameraPreviewErrorClearsReadyAndFlashState() {
        val viewModel = ScannerViewModel(importDevicePhotosUseCase = importUseCase())

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashToggleClicked)
        viewModel.onEvent(ScannerUiEvent.OnCameraPreviewError("Camera is unavailable. Please try again."))

        assertFalse(viewModel.uiState.value.isCameraReady)
        assertFalse(viewModel.uiState.value.isFlashAvailable)
        assertFalse(viewModel.uiState.value.isFlashEnabled)
        assertEquals("Camera is unavailable. Please try again.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun flashToggleRequiresReadyCameraAndFlashSupport() {
        val viewModel = ScannerViewModel(importDevicePhotosUseCase = importUseCase())

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashToggleClicked)

        assertFalse(viewModel.uiState.value.isFlashEnabled)

        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashToggleClicked)

        assertTrue(viewModel.uiState.value.isFlashEnabled)

        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(false))

        assertFalse(viewModel.uiState.value.isCameraReady)
        assertFalse(viewModel.uiState.value.isFlashEnabled)

        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashToggleClicked)

        assertTrue(viewModel.uiState.value.isFlashEnabled)

        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(false))

        assertFalse(viewModel.uiState.value.isFlashAvailable)
        assertFalse(viewModel.uiState.value.isFlashEnabled)
    }

    @Test
    fun importSuccessUpdatesSessionAndEmitsReviewNavigation() = runTest {
        val viewModel = ScannerViewModel(
            importDevicePhotosUseCase = importUseCase(
                idProvider = SequenceIdProvider(listOf("page-1"))
            )
        )
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnImportPhotosSelected(listOf("content://photo")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("created-session", viewModel.uiState.value.sessionId)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals(ScannerUiEffect.NavigateToReview("created-session"), effect.await())
    }

    @Test
    fun importFailureShowsReadableErrorAndClearsLoading() = runTest {
        val viewModel = ScannerViewModel(
            importDevicePhotosUseCase = importUseCase(
                devicePhotoRepository = FakeDevicePhotoRepository(
                    result = AppResult.Error(
                        message = "Selected image could not be read.",
                        category = AppErrorCategory.VALIDATION
                    )
                ),
                idProvider = SequenceIdProvider(listOf("page-1"))
            )
        )
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnImportPhotosSelected(listOf("content://photo")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isImporting)
        assertEquals("Selected image could not be read.", viewModel.uiState.value.errorMessage)
        assertTrue(effect.await() is ScannerUiEffect.ShowToast)
    }

    private fun importUseCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        devicePhotoRepository: FakeDevicePhotoRepository = FakeDevicePhotoRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1", "page-2")),
        timeProvider: TimeProvider = FixedTimeProvider(timestampMillis = 1L)
    ): ImportDevicePhotosUseCase = ImportDevicePhotosUseCase(
        scanRepository = scanRepository,
        devicePhotoRepository = devicePhotoRepository,
        fileRepository = fileRepository,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private class FakeScanRepository : ScanRepository {
        val addedPages: MutableList<ScannedPage> = mutableListOf()

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> = AppResult.Success(
            ScanSession(
                id = "created-session",
                createdAt = 1L,
                updatedAt = 1L,
                status = ScanSessionStatus.IN_PROGRESS,
                scanMode = scanMode
            )
        )

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> = AppResult.Success(null)

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = AppResult.Success(null)

        override suspend fun updateMetadata(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun addPage(page: ScannedPage): AppResult<Unit> {
            addedPages += page
            return AppResult.Success(Unit)
        }

        override suspend fun updatePage(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePage(pageId: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun reorderPages(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun updateSessionStatus(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeDevicePhotoRepository(
        private val result: AppResult<ImportedRawImage> = AppResult.Success(
            ImportedRawImage(path = "/raw/page-1.jpg", width = 100, height = 200)
        )
    ) : DevicePhotoRepository {
        override suspend fun importRawPhoto(
            sessionId: String,
            pageId: String,
            sourceUri: String
        ): AppResult<ImportedRawImage> = result
    }

    private class FakeFileRepository : FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class SequenceIdProvider(ids: List<String>) : IdProvider {
        private val iterator = ids.iterator()

        override fun generateId(): String {
            check(iterator.hasNext()) { "No more test IDs are available." }
            return iterator.next()
        }
    }

    private class FixedTimeProvider(private val timestampMillis: Long) : TimeProvider {
        override fun now(): Long = timestampMillis
    }

    class MainDispatcherRule(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
        TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
