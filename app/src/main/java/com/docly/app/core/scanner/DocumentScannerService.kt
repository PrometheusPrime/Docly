package com.docly.app.core.scanner

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.docly.app.core.result.AppResult

interface DocumentScannerService {
    suspend fun createScanRequest(
        activity: Activity,
        options: ScanOptions = ScanOptions()
    ): AppResult<IntentSenderRequest>

    fun parseScanResult(data: Intent?): AppResult<ScanResult>
}

data class ScanOptions(
    val allowGalleryImport: Boolean = true,
    val maxPages: Int? = null,
    val scannerMode: ScanOptionsMode = ScanOptionsMode.FULL,
    val resultFormats: Set<ScanResultFormat> = setOf(ScanResultFormat.JPEG)
)

enum class ScanOptionsMode {
    BASE,
    BASE_WITH_FILTER,
    FULL
}

enum class ScanResultFormat {
    JPEG,
    PDF
}

data class ScanResult(val pageImageUris: List<String>, val pdfUri: String? = null, val pdfPageCount: Int? = null)
