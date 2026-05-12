package com.docly.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_pages",
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ScannedPageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val scanMode: String,
    val reviewStatus: String,
    val width: Int,
    val height: Int,
    val topLeftX: Float?,
    val topLeftY: Float?,
    val topRightX: Float?,
    val topRightY: Float?,
    val bottomRightX: Float?,
    val bottomRightY: Float?,
    val bottomLeftX: Float?,
    val bottomLeftY: Float?,
    val createdAt: Long
)
