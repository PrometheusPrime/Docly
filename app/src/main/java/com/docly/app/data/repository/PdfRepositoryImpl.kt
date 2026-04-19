package com.docly.app.data.repository

import com.docly.app.core.pdf.PdfGenerator
import com.docly.app.core.result.AppResult
import com.docly.app.domain.repository.PdfRepository
import javax.inject.Inject

class PdfRepositoryImpl @Inject constructor(private val pdfGenerator: PdfGenerator) : PdfRepository {
    override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> =
        pdfGenerator.generate(pageImagePaths = pageImagePaths, outputPdfPath = outputPdfPath)
}
