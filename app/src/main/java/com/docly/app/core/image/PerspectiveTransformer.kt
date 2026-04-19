package com.docly.app.core.image

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.PageCorners
import javax.inject.Inject

data class WarpResult(val outputPath: String, val width: Int, val height: Int)

interface PerspectiveTransformer {
    suspend fun warp(imagePath: String, corners: PageCorners, outputPath: String): AppResult<WarpResult>
}

class NotImplementedPerspectiveTransformer @Inject constructor() : PerspectiveTransformer {
    override suspend fun warp(imagePath: String, corners: PageCorners, outputPath: String): AppResult<WarpResult> =
        AppResult.Error(
            message = "Perspective correction is not implemented yet.",
            category = AppErrorCategory.PROCESSING
        )
}
