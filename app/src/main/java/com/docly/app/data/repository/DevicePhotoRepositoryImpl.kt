package com.docly.app.data.repository

import android.content.Context
import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppResult
import com.docly.app.data.storage.AndroidRawPhotoSource
import com.docly.app.data.storage.BitmapFactoryImageBoundsReader
import com.docly.app.data.storage.RawPhotoImporter
import com.docly.app.domain.model.ImportedRawImage
import com.docly.app.domain.repository.DevicePhotoRepository
import com.docly.app.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DevicePhotoRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fileRepository: FileRepository,
    private val dispatcherProvider: DispatcherProvider
) : DevicePhotoRepository {
    override suspend fun importRawPhoto(
        sessionId: String,
        pageId: String,
        sourceUri: String
    ): AppResult<ImportedRawImage> = repositoryResult(dispatcherProvider) {
        RawPhotoImporter(
            fileRepository = fileRepository,
            rawPhotoSource = AndroidRawPhotoSource(context.contentResolver),
            imageBoundsReader = BitmapFactoryImageBoundsReader
        ).importRawPhoto(
            sessionId = sessionId,
            pageId = pageId,
            sourceUri = sourceUri
        )
    }
}
