package com.docly.app.core.pdf

import com.docly.app.core.result.AppResult

interface PdfGenerator {
    suspend fun generate(
        pageImagePaths: List<String>,
        outputPdfPath: String,
        options: PdfGenerationOptions = PdfGenerationOptions()
    ): AppResult<String>
}

data class PdfGenerationOptions(
    val pagePolicy: PdfPagePolicy = PdfPagePolicy.A4Fit,
    val renderQuality: PdfRenderQuality = PdfRenderQuality.High
)

sealed interface PdfPagePolicy {
    data object A4Fit : PdfPagePolicy
}

enum class PdfRenderQuality(val maxLongEdgePx: Int) {
    High(maxLongEdgePx = 2480),
    Medium(maxLongEdgePx = 1600)
}
