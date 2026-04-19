package com.docly.app.domain.usecase.session

import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScanSessionStatus
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

class UpdateScanMetadataUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String, metadata: DocumentMetadata): AppResult<Unit> =
        scanRepository.updateMetadata(sessionId, metadata)
}

class UpdateScanSessionStatusUseCase @Inject constructor(private val scanRepository: ScanRepository) {
    suspend operator fun invoke(sessionId: String, status: ScanSessionStatus): AppResult<Unit> =
        scanRepository.updateSessionStatus(sessionId, status)
}
