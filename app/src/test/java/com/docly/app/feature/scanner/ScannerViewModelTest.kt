package com.docly.app.feature.scanner

import androidx.lifecycle.SavedStateHandle
import com.docly.app.core.camera.CameraCaptureResult
import com.docly.app.core.camera.PreviewDocumentBoundary
import com.docly.app.core.camera.PreviewFrameAnalysis
import com.docly.app.core.common.IdProvider
import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.image.ScanQualityIssue
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.result.LOW_STORAGE_USER_MESSAGE
import com.docly.app.core.testing.NoOpDiagnosticsRepository
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.model.OrphanCleanupResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.PointFSerializable
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.SavedDocument
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionRecoveryDestination
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.CleanupRepository
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.usecase.page.CapturePageUseCase
import com.docly.app.domain.usecase.page.ImportDevicePhotosUseCase
import com.docly.app.domain.usecase.session.AbandonScanSessionUseCase
import com.docly.app.domain.usecase.session.CleanOrphanedFilesUseCase
import com.docly.app.domain.usecase.session.GetRecoverableSessionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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
        val viewModel = viewModel()

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
        val viewModel = viewModel()
        val previewBoundary = samplePreviewBoundary()

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnPreviewDocumentBoundaryChanged(previewBoundary))
        viewModel.onEvent(ScannerUiEvent.OnFlashAvailabilityChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnFlashToggleClicked)
        viewModel.onEvent(ScannerUiEvent.OnCameraPreviewError("Camera is unavailable. Please try again."))

        assertFalse(viewModel.uiState.value.isCameraReady)
        assertFalse(viewModel.uiState.value.isFlashAvailable)
        assertFalse(viewModel.uiState.value.isFlashEnabled)
        assertNull(viewModel.uiState.value.previewBoundary)
        assertEquals("Camera is unavailable. Please try again.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun previewBoundaryUpdatesScannerStateAndDetectedCorners() {
        val viewModel = viewModel()
        val previewBoundary = samplePreviewBoundary()

        viewModel.onEvent(ScannerUiEvent.OnPreviewDocumentBoundaryChanged(previewBoundary))

        assertEquals(previewBoundary, viewModel.uiState.value.previewBoundary)
        assertEquals(previewBoundary.corners, viewModel.uiState.value.detectedCorners)

        viewModel.onEvent(ScannerUiEvent.OnPreviewDocumentBoundaryChanged(null))

        assertNull(viewModel.uiState.value.previewBoundary)
        assertEquals(previewBoundary.corners, viewModel.uiState.value.detectedCorners)
    }

    @Test
    fun previewFrameAnalysisUpdatesBoundaryCornersAndQualityHint() {
        val viewModel = viewModel()
        val previewBoundary = samplePreviewBoundary()

        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(
                    boundary = previewBoundary,
                    quality = qualityWith(ScanQualityIssue.DOCUMENT_TOO_SMALL)
                )
            )
        )

        assertEquals(previewBoundary, viewModel.uiState.value.previewBoundary)
        assertEquals(previewBoundary.corners, viewModel.uiState.value.detectedCorners)
        assertEquals("Move closer", viewModel.uiState.value.qualityHint)
    }

    @Test
    fun previewQualityHintUsesIssuePriority() {
        val viewModel = viewModel()

        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(
                    boundary = null,
                    quality = qualityWith(ScanQualityIssue.DOCUMENT_NOT_DETECTED, ScanQualityIssue.BLURRY)
                )
            )
        )
        assertEquals("Document not detected", viewModel.uiState.value.qualityHint)

        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(
                    boundary = samplePreviewBoundary(),
                    quality = qualityWith(ScanQualityIssue.TOO_DARK)
                )
            )
        )
        assertEquals("Improve lighting", viewModel.uiState.value.qualityHint)

        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(boundary = samplePreviewBoundary(), quality = qualityWith(ScanQualityIssue.BLURRY))
            )
        )
        assertEquals("Hold steady", viewModel.uiState.value.qualityHint)
    }

    @Test
    fun autoCaptureRequestsCaptureAfterStableGoodFrames() {
        val viewModel = viewModel()
        val analysis = PreviewFrameAnalysis(boundary = samplePreviewBoundary(), quality = qualityWith())

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnAutoCaptureEnabledChanged(true))

        repeat(3) {
            viewModel.onEvent(ScannerUiEvent.OnPreviewFrameAnalysisChanged(analysis))
        }

        assertEquals(1L, viewModel.uiState.value.autoCaptureRequestId)
        assertEquals("Auto-capturing...", viewModel.uiState.value.autoCaptureHint)
    }

    @Test
    fun autoCaptureDoesNotRequestCaptureWhenQualityIsPoor() {
        val viewModel = viewModel()

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(ScannerUiEvent.OnAutoCaptureEnabledChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(boundary = samplePreviewBoundary(), quality = qualityWith(ScanQualityIssue.BLURRY))
            )
        )

        assertEquals(0L, viewModel.uiState.value.autoCaptureRequestId)
        assertEquals("Auto capture: hold steady", viewModel.uiState.value.autoCaptureHint)
    }

    @Test
    fun cameraNotReadyClearsPreviewBoundary() {
        val viewModel = viewModel()

        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnPreviewFrameAnalysisChanged(
                PreviewFrameAnalysis(
                    boundary = samplePreviewBoundary(),
                    quality = qualityWith(ScanQualityIssue.BLURRY)
                )
            )
        )
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(false))

        assertNull(viewModel.uiState.value.previewBoundary)
        assertNull(viewModel.uiState.value.qualityHint)
    }

    @Test
    fun flashToggleRequiresReadyCameraAndFlashSupport() {
        val viewModel = viewModel()

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
        val viewModel = viewModel(
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
        val viewModel = viewModel(
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

    @Test
    fun captureSuccessUpdatesSessionAndEmitsReviewNavigation() = runTest {
        val scanRepository = FakeScanRepository()
        val viewModel = viewModel(
            capturePageUseCase = captureUseCase(
                scanRepository = scanRepository,
                idProvider = SequenceIdProvider(listOf("page-1"))
            )
        )
        var captureCalls = 0
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnCaptureClicked(
                ScannerCaptureAction { outputPath ->
                    captureCalls += 1
                    AppResult.Success(CameraCaptureResult(path = outputPath, width = 300, height = 400))
                }
            )
        )
        advanceUntilIdle()

        assertEquals(1, captureCalls)
        assertFalse(viewModel.uiState.value.isCapturing)
        assertEquals("created-session", viewModel.uiState.value.sessionId)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals("/raw/created-session/page-1.jpg", scanRepository.addedPages.single().originalImagePath)
        assertEquals("/thumb/created-session/page-1.jpg", scanRepository.addedPages.single().thumbnailPath)
        assertEquals(ScannerUiEffect.NavigateToReview("created-session"), effect.await())
    }

    @Test
    fun captureFailureShowsReadableErrorAndClearsLoading() = runTest {
        val viewModel = viewModel(
            capturePageUseCase = captureUseCase(idProvider = SequenceIdProvider(listOf("page-1")))
        )
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnCaptureClicked(
                ScannerCaptureAction {
                    AppResult.Error(
                        message = "Could not capture image. Please try again.",
                        category = AppErrorCategory.CAMERA
                    )
                }
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCapturing)
        assertEquals("Could not capture image. Please try again.", viewModel.uiState.value.errorMessage)
        assertTrue(effect.await() is ScannerUiEffect.ShowToast)
    }

    @Test
    fun duplicateCaptureClicksAreIgnoredWhileCaptureIsRunning() = runTest {
        val viewModel = viewModel(
            capturePageUseCase = captureUseCase(idProvider = SequenceIdProvider(listOf("page-1", "page-2")))
        )
        var captureCalls = 0

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        val captureAction = ScannerCaptureAction { outputPath: String ->
            captureCalls += 1
            AppResult.Success(CameraCaptureResult(path = outputPath, width = 300, height = 400))
        }

        viewModel.onEvent(ScannerUiEvent.OnCaptureClicked(captureAction))
        viewModel.onEvent(ScannerUiEvent.OnCaptureClicked(captureAction))
        advanceUntilIdle()

        assertEquals(1, captureCalls)
        assertFalse(viewModel.uiState.value.isCapturing)
    }

    @Test
    fun savedSessionIdInitializesScannerStateForAdditionalPages() {
        val viewModel = viewModel(sessionId = "existing-session")

        assertEquals("existing-session", viewModel.uiState.value.sessionId)
    }

    @Test
    fun startLoadsRecoverableSessionAndRunsCleanup() = runTest {
        val scanRepository = FakeScanRepository(
            recoverableSession = sampleSession(
                id = "recoverable-session",
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
            )
        )
        val cleanupRepository = FakeCleanupRepository()
        val viewModel = viewModel(
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository),
            cleanOrphanedFilesUseCase = CleanOrphanedFilesUseCase(cleanupRepository)
        )

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()

        val prompt = viewModel.uiState.value.recoveryPrompt
        assertEquals("recoverable-session", prompt?.sessionId)
        assertEquals(1, prompt?.pageCount)
        assertEquals(ScanSessionRecoveryDestination.REVIEW, prompt?.destination)
        assertEquals(1, cleanupRepository.cleanCalls)
    }

    @Test
    fun cleanupFailureDoesNotBlockRecoveryPrompt() = runTest {
        val scanRepository = FakeScanRepository(
            recoverableSession = sampleSession(
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
            )
        )
        val cleanupRepository = FakeCleanupRepository(
            result = AppResult.Error("Cleanup failed.", AppErrorCategory.STORAGE)
        )
        val viewModel = viewModel(
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository),
            cleanOrphanedFilesUseCase = CleanOrphanedFilesUseCase(cleanupRepository)
        )

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals("session-id", viewModel.uiState.value.recoveryPrompt?.sessionId)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun startSkipsRecoveryLookupWhenSessionIdIsExplicit() = runTest {
        val scanRepository = FakeScanRepository(
            recoverableSession = sampleSession(
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
            )
        )
        val cleanupRepository = FakeCleanupRepository()
        val viewModel = viewModel(
            sessionId = "explicit-session",
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository),
            cleanOrphanedFilesUseCase = CleanOrphanedFilesUseCase(cleanupRepository)
        )

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.recoveryPrompt)
        assertEquals(0, scanRepository.recoverableLookupCalls)
        assertEquals(1, cleanupRepository.cleanCalls)
    }

    @Test
    fun resumeRecoveredSessionNavigatesToReviewEditorOrExport() = runTest {
        val reviewEffect = resumeEffectFor(
            sampleSession(pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING)))
        )
        val editorEffect = resumeEffectFor(
            sampleSession(pages = listOf(samplePage(reviewStatus = PageReviewStatus.ACCEPTED)))
        )
        val exportEffect = resumeEffectFor(
            sampleSession(
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.ACCEPTED)),
                metadata = sampleMetadata()
            )
        )

        assertEquals(ScannerUiEffect.NavigateToReview("session-id"), reviewEffect)
        assertEquals(ScannerUiEffect.NavigateToEditor("session-id"), editorEffect)
        assertEquals(ScannerUiEffect.NavigateToExport("session-id"), exportEffect)
    }

    @Test
    fun discardRecoveredSessionAbandonsSessionAndClearsPrompt() = runTest {
        val scanRepository = FakeScanRepository(
            recoverableSession = sampleSession(
                id = "recoverable-session",
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
            )
        )
        val viewModel = viewModel(
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository),
            abandonScanSessionUseCase = AbandonScanSessionUseCase(scanRepository)
        )
        val effect = async { viewModel.uiEffect.first() }

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ScannerUiEvent.OnDiscardRecoveredSessionClicked)
        advanceUntilIdle()

        assertEquals(listOf("recoverable-session"), scanRepository.abandonedSessionIds)
        assertEquals(null, viewModel.uiState.value.recoveryPrompt)
        assertEquals(ScannerUiEffect.ShowToast("Recovered scan discarded."), effect.await())
    }

    @Test
    fun recoveryPromptBlocksCaptureAndImportUntilResolved() = runTest {
        val scanRepository = FakeScanRepository(
            recoverableSession = sampleSession(
                pages = listOf(samplePage(reviewStatus = PageReviewStatus.PENDING))
            )
        )
        val viewModel = viewModel(
            capturePageUseCase = captureUseCase(
                scanRepository = scanRepository,
                idProvider = SequenceIdProvider(listOf("page-1"))
            ),
            importDevicePhotosUseCase = importUseCase(
                scanRepository = scanRepository,
                idProvider = SequenceIdProvider(listOf("page-2"))
            ),
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository)
        )
        var captureCalls = 0

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnCaptureClicked(
                ScannerCaptureAction { outputPath ->
                    captureCalls += 1
                    AppResult.Success(CameraCaptureResult(path = outputPath, width = 300, height = 400))
                }
            )
        )
        viewModel.onEvent(ScannerUiEvent.OnImportPhotosSelected(listOf("content://photo")))
        advanceUntilIdle()

        assertEquals(0, captureCalls)
        assertTrue(scanRepository.addedPages.isEmpty())
    }

    @Test
    fun lowStorageCaptureFailureShowsSpecificUserMessage() = runTest {
        val viewModel = viewModel(
            capturePageUseCase = captureUseCase(
                fileRepository = FakeFileRepository(
                    storageResult = AppResult.Error(
                        message = LOW_STORAGE_USER_MESSAGE,
                        category = AppErrorCategory.STORAGE
                    )
                )
            )
        )
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnPermissionResult(CameraPermissionStatus.Granted))
        viewModel.onEvent(ScannerUiEvent.OnCameraReadyChanged(true))
        viewModel.onEvent(
            ScannerUiEvent.OnCaptureClicked(
                ScannerCaptureAction { outputPath ->
                    AppResult.Success(CameraCaptureResult(path = outputPath, width = 300, height = 400))
                }
            )
        )
        advanceUntilIdle()

        assertEquals(LOW_STORAGE_USER_MESSAGE, viewModel.uiState.value.errorMessage)
        assertEquals(ScannerUiEffect.ShowToast(LOW_STORAGE_USER_MESSAGE), effect.await())
    }

    @Test
    fun lowStorageImportFailureShowsSpecificUserMessage() = runTest {
        val viewModel = viewModel(
            importDevicePhotosUseCase = importUseCase(
                fileRepository = FakeFileRepository(
                    storageResult = AppResult.Error(
                        message = LOW_STORAGE_USER_MESSAGE,
                        category = AppErrorCategory.STORAGE
                    )
                )
            )
        )
        val effect = async { viewModel.uiEffect.first() }
        runCurrent()

        viewModel.onEvent(ScannerUiEvent.OnImportPhotosSelected(listOf("content://photo")))
        advanceUntilIdle()

        assertEquals(LOW_STORAGE_USER_MESSAGE, viewModel.uiState.value.errorMessage)
        assertEquals(ScannerUiEffect.ShowToast(LOW_STORAGE_USER_MESSAGE), effect.await())
    }

    private fun viewModel(
        sessionId: String? = null,
        capturePageUseCase: CapturePageUseCase = captureUseCase(),
        importDevicePhotosUseCase: ImportDevicePhotosUseCase = importUseCase(),
        getRecoverableSessionUseCase: GetRecoverableSessionUseCase = GetRecoverableSessionUseCase(
            FakeScanRepository()
        ),
        abandonScanSessionUseCase: AbandonScanSessionUseCase = AbandonScanSessionUseCase(FakeScanRepository()),
        cleanOrphanedFilesUseCase: CleanOrphanedFilesUseCase = CleanOrphanedFilesUseCase(FakeCleanupRepository())
    ): ScannerViewModel = ScannerViewModel(
        savedStateHandle = if (sessionId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf("sessionId" to sessionId))
        },
        capturePageUseCase = capturePageUseCase,
        importDevicePhotosUseCase = importDevicePhotosUseCase,
        getRecoverableSessionUseCase = getRecoverableSessionUseCase,
        abandonScanSessionUseCase = abandonScanSessionUseCase,
        cleanOrphanedFilesUseCase = cleanOrphanedFilesUseCase,
        diagnosticsRepository = NoOpDiagnosticsRepository(),
        idProvider = SequenceIdProvider(listOf("diagnostic-1", "diagnostic-2", "diagnostic-3")),
        timeProvider = FixedTimeProvider(1L)
    )

    private fun captureUseCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        imageProcessingRepository: ImageProcessingRepository = FakeImageProcessingRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1", "page-2")),
        timeProvider: TimeProvider = FixedTimeProvider(timestampMillis = 1L)
    ): CapturePageUseCase = CapturePageUseCase(
        scanRepository = scanRepository,
        fileRepository = fileRepository,
        imageProcessingRepository = imageProcessingRepository,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private fun importUseCase(
        scanRepository: FakeScanRepository = FakeScanRepository(),
        devicePhotoRepository: FakeDevicePhotoRepository = FakeDevicePhotoRepository(),
        fileRepository: FakeFileRepository = FakeFileRepository(),
        imageProcessingRepository: ImageProcessingRepository = FakeImageProcessingRepository(),
        idProvider: IdProvider = SequenceIdProvider(listOf("page-1", "page-2")),
        timeProvider: TimeProvider = FixedTimeProvider(timestampMillis = 1L)
    ): ImportDevicePhotosUseCase = ImportDevicePhotosUseCase(
        scanRepository = scanRepository,
        devicePhotoRepository = devicePhotoRepository,
        fileRepository = fileRepository,
        imageProcessingRepository = imageProcessingRepository,
        idProvider = idProvider,
        timeProvider = timeProvider
    )

    private fun samplePreviewBoundary(): PreviewDocumentBoundary = PreviewDocumentBoundary(
        corners = PageCorners(
            topLeft = PointFSerializable(10f, 20f),
            topRight = PointFSerializable(90f, 25f),
            bottomRight = PointFSerializable(80f, 180f),
            bottomLeft = PointFSerializable(15f, 170f)
        ),
        imageWidth = 100,
        imageHeight = 200
    )

    private fun qualityWith(vararg issues: ScanQualityIssue): ScanQualityAssessment =
        ScanQualityAssessment.good().copy(issues = issues.toSet())

    private suspend fun TestScope.resumeEffectFor(session: ScanSession): ScannerUiEffect {
        val scanRepository = FakeScanRepository(recoverableSession = session)
        val viewModel = viewModel(
            getRecoverableSessionUseCase = GetRecoverableSessionUseCase(scanRepository)
        )
        val effect = async { viewModel.uiEffect.first() }

        viewModel.onEvent(ScannerUiEvent.OnStart)
        advanceUntilIdle()
        viewModel.onEvent(ScannerUiEvent.OnResumeRecoveredSessionClicked)
        advanceUntilIdle()

        return effect.await()
    }

    private fun sampleSession(
        id: String = "session-id",
        pages: List<ScannedPage>,
        metadata: DocumentMetadata? = null
    ): ScanSession = ScanSession(
        id = id,
        createdAt = 1L,
        updatedAt = 2L,
        status = ScanSessionStatus.IN_PROGRESS,
        scanMode = ScanMode.DOCUMENT,
        pages = pages,
        metadata = metadata
    )

    private fun samplePage(id: String = "page-id", reviewStatus: PageReviewStatus): ScannedPage = ScannedPage(
        id = id,
        sessionId = "session-id",
        pageIndex = 0,
        originalImagePath = "/raw/$id.jpg",
        processedImagePath = if (reviewStatus == PageReviewStatus.ACCEPTED) "/processed/$id.jpg" else null,
        thumbnailPath = "/thumb/$id.jpg",
        rotationDegrees = 0,
        scanMode = ScanMode.DOCUMENT,
        width = 100,
        height = 200,
        createdAt = 1L,
        reviewStatus = reviewStatus
    )

    private fun sampleMetadata(): DocumentMetadata = DocumentMetadata(
        grade = "Grade 10",
        subject = "Math",
        year = 2026,
        paperType = "Past Paper"
    )

    private class FakeScanRepository(
        var recoverableSession: ScanSession? = null,
        private val abandonResult: AppResult<Unit> = AppResult.Success(Unit)
    ) : ScanRepository {
        val sessionsById: MutableMap<String, ScanSession> = mutableMapOf()
        val addedPages: MutableList<ScannedPage> = mutableListOf()
        val abandonedSessionIds: MutableList<String> = mutableListOf()
        var recoverableLookupCalls = 0

        override suspend fun createSession(scanMode: ScanMode): AppResult<ScanSession> {
            val session = ScanSession(
                id = "created-session",
                createdAt = 1L,
                updatedAt = 1L,
                status = ScanSessionStatus.IN_PROGRESS,
                scanMode = scanMode
            )
            sessionsById[session.id] = session
            return AppResult.Success(session)
        }

        override suspend fun getSession(sessionId: String): AppResult<ScanSession?> =
            AppResult.Success(sessionsById[sessionId])

        override suspend fun getLatestInProgressSession(): AppResult<ScanSession?> = AppResult.Success(null)

        override suspend fun getLatestRecoverableSession(): AppResult<ScanSession?> {
            recoverableLookupCalls += 1
            return AppResult.Success(recoverableSession)
        }

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

        override suspend fun abandonSession(sessionId: String): AppResult<Unit> {
            abandonedSessionIds += sessionId
            if (abandonResult is AppResult.Success) {
                recoverableSession = null
            }
            return abandonResult
        }
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

    private class FakeFileRepository(private val storageResult: AppResult<Unit> = AppResult.Success(Unit)) :
        FileRepository {
        override fun createSessionImagePath(sessionId: String, suffix: String): String = "/raw/$sessionId/$suffix.jpg"

        override fun createProcessedImagePath(sessionId: String, suffix: String): String =
            "/processed/$sessionId/$suffix.jpg"

        override fun createThumbnailPath(sessionId: String, suffix: String): String = "/thumb/$sessionId/$suffix.jpg"

        override fun createPdfPath(fileName: String): String = "/pdf/$fileName"

        override suspend fun ensureStorageAvailable(requiredBytes: Long): AppResult<Unit> = storageResult

        override suspend fun deleteFile(path: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteFiles(paths: List<String>): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deletePageAssets(page: ScannedPage): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSessionAssets(session: ScanSession): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun deleteSavedDocumentAssets(document: SavedDocument): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeCleanupRepository(
        private val result: AppResult<OrphanCleanupResult> = AppResult.Success(
            OrphanCleanupResult(deletedFileCount = 0)
        )
    ) : CleanupRepository {
        var cleanCalls = 0

        override suspend fun cleanOrphanedFiles(): AppResult<OrphanCleanupResult> {
            cleanCalls += 1
            return result
        }
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

    private class FakeImageProcessingRepository : ImageProcessingRepository {
        override suspend fun detectDocument(inputPath: String): AppResult<PageCorners?> = AppResult.Success(null)

        override suspend fun processPage(
            inputPath: String,
            processedOutputPath: String,
            thumbnailOutputPath: String,
            scanMode: ScanMode,
            corners: PageCorners?
        ): AppResult<ProcessedPageResult> = AppResult.Error(
            message = "Not implemented.",
            category = AppErrorCategory.PROCESSING
        )

        override suspend fun generateThumbnail(inputPath: String, outputPath: String): AppResult<String> =
            AppResult.Success(outputPath)
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
