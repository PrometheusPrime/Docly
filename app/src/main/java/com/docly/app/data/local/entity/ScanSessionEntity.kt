package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
    val scanMode: String,
    val grade: String?,
    val subject: String?,
    val year: Int?,
    val paperType: String?,
    val paperNumber: String?,
    val source: String?,
    val notes: String?
)
