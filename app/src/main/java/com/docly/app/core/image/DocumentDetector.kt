package com.docly.app.core.image

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import javax.inject.Inject

interface DocumentDetector {
    suspend fun detect(imagePath: String): AppResult<PageCorners?>
}

class NotImplementedDocumentDetector @Inject constructor() : DocumentDetector {
    override suspend fun detect(imagePath: String): AppResult<PageCorners?> = AppResult.Error(
        message = "Document boundary detection is not implemented yet.",
        category = AppErrorCategory.PROCESSING
    )
}
