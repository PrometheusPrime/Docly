package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_events")
data class DiagnosticEventEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val stage: String,
    val severity: String,
    val message: String,
    val relatedDocumentId: String?,
    val relatedSessionId: String?,
    val relatedPageId: String?,
    val throwableClass: String?
)
