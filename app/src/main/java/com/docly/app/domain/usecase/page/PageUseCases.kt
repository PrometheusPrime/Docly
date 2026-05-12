package com.docly.app.domain.usecase.page

import com.docly.app.core.camera.CameraCaptureResult
import com.docly.app.core.common.IdProvider
import com.docly.app.core.image.ScanQualityAssessment
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.CapturePageResult
import com.docly.app.domain.model.ImportDevicePhotosResult
import com.docly.app.domain.model.PageCorners
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ProcessedPageResult
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.model.ScannedPage
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.FileRepository
import com.docly.app.domain.repository.ImageProcessingRepository
import com.docly.app.domain.repository.ScanRepository
import com.docly.app.domain.repository.StorageReserveBytes
import javax.inject.Inject

class CapturePageUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val fileRepository: FileRepository,
    private val imageProcessingRepository: ImageProcessingRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        sessionId: String?,
        scanMode: ScanMode,
        captureToFile: suspend (outputPath: String) -> AppResult<CameraCaptureResult>
    ): AppResult<CapturePageResult> {
        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.CAPTURE_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        val session = when (val sessionResult = resolveInProgressSession(scanRepository, sessionId, scanMode)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data
        }

        val pageId = idProvider.generateId()
        val rawImagePath = fileRepository.createSessionImagePath(sessionId = session.id, suffix = pageId)
        val captureResult = when (val result = captureSafely(rawImagePath, captureToFile)) {
            is AppResult.Error -> {
                fileRepository.deleteFile(rawImagePath)
                return result
            }

            is AppResult.Success -> result.data
        }
        val thumbnailPath = fileRepository.createThumbnailPath(sessionId = session.id, suffix = pageId)
        val generatedThumbnailPath = when (
            val thumbnailResult = imageProcessingRepository.generateThumbnail(
                inputPath = captureResult.path,
                outputPath = thumbnailPath
            )
        ) {
            is AppResult.Error -> {
                fileRepository.deleteFiles(listOf(captureResult.path, thumbnailPath))
                return thumbnailResult
            }

            is AppResult.Success -> thumbnailResult.data
        }
        val detectedCorners = detectCornersRecoverably(captureResult.path)
        val page = ScannedPage(
            id = pageId,
            sessionId = session.id,
            pageIndex = session.pages.maxOfOrNull { page -> page.pageIndex }?.plus(1) ?: 0,
            originalImagePath = captureResult.path,
            processedImagePath = null,
            thumbnailPath = generatedThumbnailPath,
            rotationDegrees = 0,
            scanMode = scanMode,
            width = captureResult.width,
            height = captureResult.height,
            corners = detectedCorners,
            createdAt = timeProvider.now(),
            reviewStatus = PageReviewStatus.PENDING
        )

        return when (val addPageResult = scanRepository.addPage(page)) {
            is AppResult.Error -> {
                fileRepository.deleteFiles(listOf(captureResult.path, generatedThumbnailPath))
                addPageResult
            }

            is AppResult.Success -> AppResult.Success(CapturePageResult(sessionId = session.id, page = page))
        }
    }

    private suspend fun captureSafely(
        outputPath: String,
        captureToFile: suspend (outputPath: String) -> AppResult<CameraCaptureResult>
    ): AppResult<CameraCaptureResult> = try {
        captureToFile(outputPath)
    } catch (throwable: Throwable) {
        AppResult.Error(
            message = CAPTURE_FAILED_MESSAGE,
            category = AppErrorCategory.CAMERA,
            throwable = throwable
        )
    }

    private suspend fun detectCornersRecoverably(inputPath: String): PageCorners? = try {
        when (val result = imageProcessingRepository.detectDocument(inputPath)) {
            is AppResult.Error -> null
            is AppResult.Success -> result.data
        }
    } catch (throwable: Throwable) {
        null
    }

    private companion object {
        const val CAPTURE_FAILED_MESSAGE = "Could not capture image. Please try again."
    }
}

class ProcessCapturedPageUseCase @Inject constructor(
    private val imageProcessingRepository: ImageProcessingRepository,
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        rawImagePath: String,
        scanMode: ScanMode,
        manualCorners: PageCorners? = null
    ): AppResult<ProcessedPageResult> = AppResult.Error(
        message = "Captured page processing is not implemented yet.",
        category = AppErrorCategory.PROCESSING
    )
}

class EvaluateScanQualityUseCase @Inject constructor(private val imageProcessingRepository: ImageProcessingRepository) {
    suspend operator fun invoke(inputPath: String, corners: PageCorners?): AppResult<ScanQualityAssessment> =
        imageProcessingRepository.evaluateQuality(inputPath = inputPath, corners = corners)
}

class ApplyPageCropUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val imageProcessingRepository: ImageProcessingRepository,
    private val fileRepository: FileRepository,
    private val idProvider: IdProvider
) {
    suspend operator fun invoke(page: ScannedPage, corners: PageCorners): AppResult<ScannedPage> {
        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.CAPTURE_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        val outputSuffix = "${page.id}_${idProvider.generateId()}"
        val processedOutputPath = fileRepository.createProcessedImagePath(
            sessionId = page.sessionId,
            suffix = outputSuffix
        )
        val thumbnailOutputPath = fileRepository.createThumbnailPath(
            sessionId = page.sessionId,
            suffix = outputSuffix
        )
        val generatedPaths = listOf(processedOutputPath, thumbnailOutputPath)

        val processedPage = when (
            val processResult = imageProcessingRepository.processPage(
                inputPath = page.originalImagePath,
                processedOutputPath = processedOutputPath,
                thumbnailOutputPath = thumbnailOutputPath,
                scanMode = page.scanMode,
                corners = corners
            )
        ) {
            is AppResult.Error -> {
                fileRepository.deleteFiles(generatedPaths)
                return processResult
            }

            is AppResult.Success -> processResult.data
        }

        val updatedPage = page.copy(
            processedImagePath = processedPage.processedImagePath,
            thumbnailPath = processedPage.thumbnailPath,
            corners = processedPage.detectedCorners ?: corners
        )

        return when (val updateResult = scanRepository.updatePage(updatedPage)) {
            is AppResult.Error -> {
                fileRepository.deleteFiles(generatedPaths)
                updateResult
            }

            is AppResult.Success -> {
                fileRepository.deleteFiles(page.replacedCropAssetPaths(updatedPage))
                AppResult.Success(updatedPage)
            }
        }
    }

    private fun ScannedPage.replacedCropAssetPaths(updatedPage: ScannedPage): List<String> {
        val retainedPaths = setOfNotNull(
            originalImagePath,
            updatedPage.processedImagePath,
            updatedPage.thumbnailPath
        )
        return listOfNotNull(processedImagePath, thumbnailPath)
            .filter { path -> path.isNotBlank() && path !in retainedPaths }
            .distinct()
    }
}

class AddProcessedPageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<Unit> = scanRepository.addPage(page)
}

class ImportDevicePhotosUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val devicePhotoRepository: DevicePhotoRepository,
    private val fileRepository: FileRepository,
    private val imageProcessingRepository: ImageProcessingRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(
        sessionId: String?,
        sourceUris: List<String>,
        scanMode: ScanMode
    ): AppResult<ImportDevicePhotosResult> {
        val normalizedUris = sourceUris
            .map { sourceUri -> sourceUri.trim() }
            .filter { sourceUri -> sourceUri.isNotBlank() }
        if (normalizedUris.isEmpty()) {
            return AppResult.Error(
                message = "Select at least one photo to import.",
                category = AppErrorCategory.VALIDATION
            )
        }

        when (val storageResult = fileRepository.ensureStorageAvailable(StorageReserveBytes.CAPTURE_BYTES)) {
            is AppResult.Error -> return storageResult
            is AppResult.Success -> Unit
        }

        val session = when (val sessionResult = resolveInProgressSession(scanRepository, sessionId, scanMode)) {
            is AppResult.Error -> return sessionResult
            is AppResult.Success -> sessionResult.data
        }
        val importedPages = mutableListOf<ScannedPage>()
        val firstPageIndex = session.pages.maxOfOrNull { page -> page.pageIndex }?.plus(1) ?: 0

        normalizedUris.forEachIndexed { offset, sourceUri ->
            val pageId = idProvider.generateId()
            val importedRawImage = when (
                val importResult = devicePhotoRepository.importRawPhoto(
                    sessionId = session.id,
                    pageId = pageId,
                    sourceUri = sourceUri
                )
            ) {
                is AppResult.Error -> {
                    rollbackImportedPages(importedPages)
                    return importResult
                }

                is AppResult.Success -> importResult.data
            }
            val thumbnailPath = fileRepository.createThumbnailPath(sessionId = session.id, suffix = pageId)
            val generatedThumbnailPath = when (
                val thumbnailResult = imageProcessingRepository.generateThumbnail(
                    inputPath = importedRawImage.path,
                    outputPath = thumbnailPath
                )
            ) {
                is AppResult.Error -> {
                    fileRepository.deleteFiles(listOf(importedRawImage.path, thumbnailPath))
                    rollbackImportedPages(importedPages)
                    return thumbnailResult
                }

                is AppResult.Success -> thumbnailResult.data
            }
            val detectedCorners = detectCornersRecoverably(importedRawImage.path)

            val page = ScannedPage(
                id = pageId,
                sessionId = session.id,
                pageIndex = firstPageIndex + offset,
                originalImagePath = importedRawImage.path,
                processedImagePath = null,
                thumbnailPath = generatedThumbnailPath,
                rotationDegrees = 0,
                scanMode = scanMode,
                width = importedRawImage.width,
                height = importedRawImage.height,
                corners = detectedCorners,
                createdAt = timeProvider.now(),
                reviewStatus = PageReviewStatus.PENDING
            )

            when (val addPageResult = scanRepository.addPage(page)) {
                is AppResult.Error -> {
                    fileRepository.deleteFiles(listOf(importedRawImage.path, generatedThumbnailPath))
                    rollbackImportedPages(importedPages)
                    return addPageResult
                }

                is AppResult.Success -> importedPages += page
            }
        }

        return AppResult.Success(
            ImportDevicePhotosResult(
                sessionId = session.id,
                importedPages = importedPages
            )
        )
    }

    private suspend fun rollbackImportedPages(importedPages: List<ScannedPage>) {
        importedPages.asReversed().forEach { page ->
            scanRepository.deletePage(page.id)
        }
    }

    private suspend fun detectCornersRecoverably(inputPath: String): PageCorners? = try {
        when (val result = imageProcessingRepository.detectDocument(inputPath)) {
            is AppResult.Error -> null
            is AppResult.Success -> result.data
        }
    } catch (throwable: Throwable) {
        null
    }
}

private suspend fun resolveInProgressSession(
    scanRepository: ScanRepository,
    sessionId: String?,
    scanMode: ScanMode
): AppResult<ScanSession> {
    if (!sessionId.isNullOrBlank()) {
        when (val sessionResult = scanRepository.getSession(sessionId)) {
            is AppResult.Error -> return sessionResult

            is AppResult.Success -> {
                val session = sessionResult.data
                if (session?.status == ScanSessionStatus.IN_PROGRESS) {
                    return AppResult.Success(session)
                }
            }
        }
    }

    when (val latestSessionResult = scanRepository.getLatestInProgressSession()) {
        is AppResult.Error -> return latestSessionResult

        is AppResult.Success -> {
            val latestSession = latestSessionResult.data
            if (latestSession != null) {
                return AppResult.Success(latestSession)
            }
        }
    }

    return scanRepository.createSession(scanMode)
}

class UpdatePageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<Unit> = scanRepository.updatePage(page)
}

class AcceptReviewedPageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<ScannedPage> {
        if (page.reviewStatus == PageReviewStatus.ACCEPTED) {
            return AppResult.Success(page)
        }

        if (page.processedImagePath.isNullOrBlank()) {
            return AppResult.Error(
                message = "Process this page before accepting it.",
                category = AppErrorCategory.VALIDATION
            )
        }

        val acceptedPage = page.copy(reviewStatus = PageReviewStatus.ACCEPTED)
        return when (val updateResult = scanRepository.updatePage(acceptedPage)) {
            is AppResult.Error -> updateResult
            is AppResult.Success -> AppResult.Success(acceptedPage)
        }
    }
}

class DeletePageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(pageId: String): AppResult<Unit> = scanRepository.deletePage(pageId)
}

class ReorderPagesUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String, orderedPageIds: List<String>): AppResult<Unit> =
        scanRepository.reorderPages(sessionId, orderedPageIds)
}

class RotatePageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<Unit> {
        val nextRotation = (page.rotationDegrees + RIGHT_ANGLE_DEGREES) % FULL_ROTATION_DEGREES
        return scanRepository.updatePage(page.copy(rotationDegrees = nextRotation))
    }

    private companion object {
        const val RIGHT_ANGLE_DEGREES = 90
        const val FULL_ROTATION_DEGREES = 360
    }
}
