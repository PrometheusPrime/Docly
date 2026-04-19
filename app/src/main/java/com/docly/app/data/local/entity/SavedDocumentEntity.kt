package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_documents")
data class SavedDocumentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val title: String,
    val pdfPath: String,
    val thumbnailPath: String?,
    val grade: String,
    val subject: String,
    val year: Int,
    val paperType: String,
    val paperNumber: String?,
    val source: String?,
    val notes: String?,
    val pageCount: Int,
    val createdAt: Long
)
