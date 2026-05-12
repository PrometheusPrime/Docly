package com.docly.app.data.local.mapper

import com.docly.app.data.local.entity.ConversionJobEntity
import com.docly.app.data.local.entity.DocumentEntity
import com.docly.app.data.local.entity.FolderEntity
import com.docly.app.data.local.entity.RecentDocumentEntity
import com.docly.app.domain.model.ConversionJob
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.Folder
import com.docly.app.domain.model.RecentDocument

fun DocumentEntity.toDomain(): DoclyDocument = DoclyDocument(
    id = id,
    name = name,
    type = type.toDocumentType(),
    mimeType = mimeType,
    fileRef = if (filePath != null) FileRef.InternalFile(filePath) else FileRef.ExternalUri(uri.orEmpty()),
    source = source.toDocumentSource(),
    folderId = folderId,
    thumbnailPath = thumbnailPath,
    fileSize = fileSize,
    pageCount = pageCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastOpenedAt = lastOpenedAt,
    isFavorite = isFavorite,
    isScanned = isScanned,
    ocrStatus = ocrStatus.toOcrStatus(),
    sourceScanSessionId = sourceScanSessionId
)

fun DoclyDocument.toEntity(): DocumentEntity {
    val internalFile = fileRef as? FileRef.InternalFile
    val externalUri = fileRef as? FileRef.ExternalUri
    return DocumentEntity(
        id = id,
        name = name,
        type = type.name,
        mimeType = mimeType,
        filePath = internalFile?.path,
        uri = externalUri?.uri,
        source = source.name,
        folderId = folderId,
        thumbnailPath = thumbnailPath,
        fileSize = fileSize,
        pageCount = pageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        isFavorite = isFavorite,
        isScanned = isScanned,
        ocrStatus = ocrStatus.name,
        sourceScanSessionId = sourceScanSessionId
    )
}

fun FolderEntity.toDomain(): Folder = Folder(
    id = id,
    name = name,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id,
    name = name,
    parentId = parentId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RecentDocumentEntity.toDomain(): RecentDocument = RecentDocument(
    documentId = documentId,
    openedAt = openedAt
)

fun RecentDocument.toEntity(): RecentDocumentEntity = RecentDocumentEntity(
    documentId = documentId,
    openedAt = openedAt
)

fun ConversionJobEntity.toDomain(): ConversionJob = ConversionJob(
    id = id,
    inputDocumentId = inputDocumentId,
    inputUri = inputUri,
    inputType = inputType.toDocumentType(),
    outputType = outputType.toDocumentType(),
    outputPath = outputPath,
    outputDocumentId = outputDocumentId,
    status = status.toConversionStatus(),
    progress = progress,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ConversionJob.toEntity(): ConversionJobEntity = ConversionJobEntity(
    id = id,
    inputDocumentId = inputDocumentId,
    inputUri = inputUri,
    inputType = inputType.name,
    outputType = outputType.name,
    outputPath = outputPath,
    outputDocumentId = outputDocumentId,
    status = status.name,
    progress = progress,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)
