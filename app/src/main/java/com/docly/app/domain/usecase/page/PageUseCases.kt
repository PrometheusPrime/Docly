package com.docly.app.domain.usecase.page

import com.docly.app.core.common.IdProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.core.time.TimeProvider
import com.docly.app.domain.model.ImportDevicePhotosResult
import com.docly.app.domain.model.PageCorners
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

class CapturePageUseCase @Inject constructor() {
    suspend operator fun invoke(sessionId: String, scanMode: ScanMode): AppResult<String> = AppResult.Error(
        message = "Camera capture is not implemented yet.",
        category = AppErrorCategory.CAMERA
    )
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

class AddProcessedPageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<Unit> = scanRepository.addPage(page)
}

class ImportDevicePhotosUseCase @Inject constructor(
    private val scanRepository: ScanRepository,
    private val devicePhotoRepository: DevicePhotoRepository,
    private val fileRepository: FileRepository,
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

        val session = when (val sessionResult = resolveSession(sessionId = sessionId, scanMode = scanMode)) {
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

            val page = ScannedPage(
                id = pageId,
                sessionId = session.id,
                pageIndex = firstPageIndex + offset,
                originalImagePath = importedRawImage.path,
                processedImagePath = null,
                thumbnailPath = null,
                rotationDegrees = 0,
                scanMode = scanMode,
                width = importedRawImage.width,
                height = importedRawImage.height,
                corners = null,
                createdAt = timeProvider.now()
            )

            when (val addPageResult = scanRepository.addPage(page)) {
                is AppResult.Error -> {
                    fileRepository.deleteFile(importedRawImage.path)
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

    private suspend fun resolveSession(sessionId: String?, scanMode: ScanMode): AppResult<ScanSession> {
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

    private suspend fun rollbackImportedPages(importedPages: List<ScannedPage>) {
        importedPages.asReversed().forEach { page ->
            scanRepository.deletePage(page.id)
        }
    }
}

class UpdatePageUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(page: ScannedPage): AppResult<Unit> = scanRepository.updatePage(page)
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
