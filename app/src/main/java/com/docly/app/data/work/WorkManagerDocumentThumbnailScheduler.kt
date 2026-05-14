package com.docly.app.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.DocumentThumbnailScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManagerDocumentThumbnailScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DocumentThumbnailScheduler {
    override fun schedule(document: DoclyDocument) {
        if (!document.needsGeneratedThumbnail()) return

        val request = OneTimeWorkRequestBuilder<DocumentThumbnailWorker>()
            .setInputData(workDataOf(DocumentThumbnailWorker.KEY_DOCUMENT_ID to document.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(document.id),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun DoclyDocument.needsGeneratedThumbnail(): Boolean {
        if (!thumbnailPath.isNullOrBlank()) return false
        if (fileRef !is FileRef.InternalFile) return false
        return type == DocumentType.IMAGE || type == DocumentType.PDF
    }

    private fun uniqueWorkName(documentId: String): String = "document-thumbnail-$documentId"
}
