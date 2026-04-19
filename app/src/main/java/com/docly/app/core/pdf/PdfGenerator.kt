package com.docly.app.core.pdf

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import javax.inject.Inject

interface PdfGenerator {
    suspend fun generate(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String>
}

class NotImplementedPdfGenerator @Inject constructor() : PdfGenerator {
    override suspend fun generate(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> =
        AppResult.Error(
            message = "PDF generation is not implemented yet.",
            category = AppErrorCategory.PDF
        )
}
