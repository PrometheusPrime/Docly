package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.DiagnosticEventEntity
import com.docly.app.domain.model.DiagnosticEvent

fun DiagnosticEventEntity.toDomain(): DiagnosticEvent = DiagnosticEvent(
    id = id,
    timestampMillis = timestampMillis,
    stage = stage.toDiagnosticStage(),
    severity = severity.toDiagnosticSeverity(),
    message = message,
    relatedDocumentId = relatedDocumentId,
    relatedSessionId = relatedSessionId,
    relatedPageId = relatedPageId,
    throwableClass = throwableClass
)

fun DiagnosticEvent.toEntity(): DiagnosticEventEntity = DiagnosticEventEntity(
    id = id,
    timestampMillis = timestampMillis,
    stage = stage.name,
    severity = severity.name,
    message = message,
    relatedDocumentId = relatedDocumentId,
    relatedSessionId = relatedSessionId,
    relatedPageId = relatedPageId,
    throwableClass = throwableClass
)
