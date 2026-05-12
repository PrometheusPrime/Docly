package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["type"]),
        Index(value = ["updatedAt"]),
        Index(value = ["lastOpenedAt"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val mimeType: String?,
    val filePath: String?,
    val uri: String?,
    val source: String,
    val folderId: String?,
    val thumbnailPath: String?,
    val fileSize: Long,
    val pageCount: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val isFavorite: Boolean,
    val isScanned: Boolean,
    val ocrStatus: String,
    val sourceScanSessionId: String?
)
