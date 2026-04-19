package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.ScanSessionEntity
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.ScanSession
import com.docly.app.domain.model.ScannedPage

fun ScanSessionEntity.toDomain(pages: List<ScannedPage> = emptyList()): ScanSession = ScanSession(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status.toScanSessionStatus(),
    scanMode = scanMode.toScanMode(),
    pages = pages,
    metadata = toMetadataOrNull()
)

fun ScanSession.toEntity(): ScanSessionEntity = ScanSessionEntity(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = status.name,
    scanMode = scanMode.name,
    grade = metadata?.grade,
    subject = metadata?.subject,
    year = metadata?.year,
    paperType = metadata?.paperType,
    paperNumber = metadata?.paperNumber,
    source = metadata?.source,
    notes = metadata?.notes
)

private fun ScanSessionEntity.toMetadataOrNull(): DocumentMetadata? {
    val metadataGrade = grade ?: return null
    val metadataSubject = subject ?: return null
    val metadataYear = year ?: return null
    val metadataPaperType = paperType ?: return null

    return DocumentMetadata(
        grade = metadataGrade,
        subject = metadataSubject,
        year = metadataYear,
        paperType = metadataPaperType,
        paperNumber = paperNumber,
        source = source,
        notes = notes
    )
}
