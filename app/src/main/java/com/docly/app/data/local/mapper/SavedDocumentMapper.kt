package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.SavedDocumentEntity
import com.docly.app.domain.model.DocumentMetadata
import com.docly.app.domain.model.SavedDocument

fun SavedDocumentEntity.toDomain(): SavedDocument = SavedDocument(
    id = id,
    sessionId = sessionId,
    title = title,
    pdfPath = pdfPath,
    thumbnailPath = thumbnailPath,
    metadata = DocumentMetadata(
        grade = grade,
        subject = subject,
        year = year,
        paperType = paperType,
        paperNumber = paperNumber,
        source = source,
        notes = notes
    ),
    pageCount = pageCount,
    createdAt = createdAt
)

fun SavedDocument.toEntity(): SavedDocumentEntity = SavedDocumentEntity(
    id = id,
    sessionId = sessionId,
    title = title,
    pdfPath = pdfPath,
    thumbnailPath = thumbnailPath,
    grade = metadata.grade,
    subject = metadata.subject,
    year = metadata.year,
    paperType = metadata.paperType,
    paperNumber = metadata.paperNumber,
    source = metadata.source,
    notes = metadata.notes,
    pageCount = pageCount,
    createdAt = createdAt
)
