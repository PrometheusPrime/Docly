package com.docly.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.docly.app.core.result.AppResult
import com.docly.app.domain.usecase.library.GenerateDocumentThumbnailUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DocumentThumbnailWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val generateDocumentThumbnailUseCase: GenerateDocumentThumbnailUseCase
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID).orEmpty()
        if (documentId.isBlank()) return Result.failure()

        return when (generateDocumentThumbnailUseCase(documentId)) {
            is AppResult.Error -> Result.success()
            is AppResult.Success -> Result.success()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
    }
}
