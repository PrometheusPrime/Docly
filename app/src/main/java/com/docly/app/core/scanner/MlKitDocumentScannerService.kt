package com.docly.app.core.scanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.IntentSenderRequest
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MlKitDocumentScannerService @Inject constructor() : DocumentScannerService {
    override suspend fun createScanRequest(activity: Activity, options: ScanOptions): AppResult<IntentSenderRequest> =
        try {
            val scanner = GmsDocumentScanning.getClient(options.toMlKitOptions())
            val intentSender = scanner.awaitStartScanIntent(activity)
            AppResult.Success(IntentSenderRequest.Builder(intentSender).build())
        } catch (throwable: Throwable) {
            AppResult.Error(
                message = "Document scanner is unavailable on this device.",
                category = AppErrorCategory.CAMERA,
                throwable = throwable
            )
        }

    override fun parseScanResult(data: Intent?): AppResult<ScanResult> {
        if (data == null) {
            return AppResult.Error(
                message = "Scan was canceled.",
                category = AppErrorCategory.VALIDATION
            )
        }

        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
            ?: return AppResult.Error(
                message = "Could not read the scan result.",
                category = AppErrorCategory.PROCESSING
            )
        val pageImageUris = result.pages.orEmpty()
            .map { page -> page.imageUri.toString() }
            .filter { uri -> uri.isNotBlank() }
        val pdf = result.pdf
        val scanResult = ScanResult(
            pageImageUris = pageImageUris,
            pdfUri = pdf?.uri?.toString(),
            pdfPageCount = pdf?.pageCount
        )

        return if (scanResult.pageImageUris.isEmpty() && scanResult.pdfUri.isNullOrBlank()) {
            AppResult.Error(
                message = "No scanned pages were returned.",
                category = AppErrorCategory.VALIDATION
            )
        } else {
            AppResult.Success(scanResult)
        }
    }

    private fun ScanOptions.toMlKitOptions(): GmsDocumentScannerOptions {
        val resultFormatValues = resultFormats.ifEmpty { setOf(ScanResultFormat.JPEG) }
            .map { format -> format.toMlKitFormat() }
        val builder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGalleryImport)
            .setScannerMode(scannerMode.toMlKitMode())
            .setResultFormats(
                resultFormatValues.first(),
                *resultFormatValues.drop(1).toIntArray()
            )
        maxPages?.takeIf { pageLimit -> pageLimit > 0 }?.let { pageLimit ->
            builder.setPageLimit(pageLimit)
        }
        return builder.build()
    }

    private fun ScanOptionsMode.toMlKitMode(): Int = when (this) {
        ScanOptionsMode.BASE -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
        ScanOptionsMode.BASE_WITH_FILTER -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
        ScanOptionsMode.FULL -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
    }

    private fun ScanResultFormat.toMlKitFormat(): Int = when (this) {
        ScanResultFormat.JPEG -> GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
        ScanResultFormat.PDF -> GmsDocumentScannerOptions.RESULT_FORMAT_PDF
    }

    private suspend fun GmsDocumentScanner.awaitStartScanIntent(activity: Activity): IntentSender =
        suspendCancellableCoroutine { continuation ->
            val task = getStartScanIntent(activity)
            task.addOnSuccessListener { intentSender ->
                if (continuation.isActive) {
                    continuation.resume(intentSender)
                }
            }
            task.addOnFailureListener { throwable ->
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
            task.addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Document scanner request was canceled."))
                }
            }
        }
}
