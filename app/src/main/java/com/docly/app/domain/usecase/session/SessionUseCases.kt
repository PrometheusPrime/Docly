package com.docly.app.domain.usecase.session

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.OrphanCleanupResult
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.RecoverableScanSession
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionRecoveryDestination
import com.docly.app.domain.model.ScanSessionStatus
import com.docly.app.domain.repository.CleanupRepository
import com.docly.app.domain.repository.ScanRepository
import javax.inject.Inject

class CreateScanSessionUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(scanMode: ScanMode): AppResult<ScanSession> = scanRepository.createSession(scanMode)
}

class GetScanSessionUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String): AppResult<ScanSession?> = scanRepository.getSession(sessionId)
}

class GetLatestInProgressSessionUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(): AppResult<ScanSession?> = scanRepository.getLatestInProgressSession()
}

class GetRecoverableSessionUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(): AppResult<RecoverableScanSession?> = when (
        val result = scanRepository.getLatestRecoverableSession()
    ) {
        is AppResult.Error -> result

        is AppResult.Success -> {
            val session = result.data?.takeIf { session -> session.pages.isNotEmpty() }
            AppResult.Success(session?.toRecoverableSession())
        }
    }
}

class UpdateScanMetadataUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
        scanRepository.updateMetadata(sessionId, metadata)
}

class UpdateScanSessionStatusUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
        scanRepository.updateSessionStatus(sessionId, status)
}

class AbandonScanSessionUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String): AppResult<Unit> = scanRepository.abandonSession(sessionId)
}

class CleanOrphanedFilesUseCase @Inject constructor(private val cleanupRepository: CleanupRepository) {
    suspend operator fun invoke(): AppResult<OrphanCleanupResult> = cleanupRepository.cleanOrphanedFiles()
}

private fun ScanSession.toRecoverableSession(): RecoverableScanSession = RecoverableScanSession(
    session = this,
    destination = when {
        pages.any { page -> page.reviewStatus == PageReviewStatus.PENDING } -> ScanSessionRecoveryDestination.REVIEW
        metadata != null -> ScanSessionRecoveryDestination.EXPORT
        else -> ScanSessionRecoveryDestination.EDITOR
    }
)
