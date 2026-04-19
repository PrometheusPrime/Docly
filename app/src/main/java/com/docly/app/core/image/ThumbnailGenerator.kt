package com.docly.app.core.image

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import javax.inject.Inject

interface ThumbnailGenerator {
    suspend fun generate(inputPath: String, outputPath: String): AppResult<String>
}

class NotImplementedThumbnailGenerator @Inject constructor() : ThumbnailGenerator {
    override suspend fun generate(inputPath: String, outputPath: String): AppResult<String> = AppResult.Error(
        message = "Thumbnail generation is not implemented yet.",
        category = AppErrorCategory.PROCESSING
    )
}
