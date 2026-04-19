package com.docly.app.core.image

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.ScanMode
import javax.inject.Inject

interface ImageEnhancer {
    suspend fun enhance(inputPath: String, outputPath: String, scanMode: ScanMode): AppResult<String>
}

class NotImplementedImageEnhancer @Inject constructor() : ImageEnhancer {
    override suspend fun enhance(inputPath: String, outputPath: String, scanMode: ScanMode): AppResult<String> =
        AppResult.Error(
            message = "Image enhancement is not implemented yet.",
            category = AppErrorCategory.PROCESSING
        )
}
