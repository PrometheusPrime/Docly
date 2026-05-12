package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversion_jobs",
    indices = [
        Index(value = ["inputDocumentId"]),
        Index(value = ["outputDocumentId"]),
        Index(value = ["updatedAt"])
    ]
)
data class ConversionJobEntity(
    @PrimaryKey val id: String,
    val inputDocumentId: String?,
    val inputUri: String?,
    val inputType: String,
    val outputType: String,
    val outputPath: String?,
    val outputDocumentId: String?,
    val status: String,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
