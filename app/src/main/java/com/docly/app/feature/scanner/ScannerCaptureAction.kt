package com.docly.app.feature.scanner

import com.docly.app.core.camera.CameraCaptureResult
import com.docly.app.core.result.AppResult

fun interface ScannerCaptureAction {
    suspend fun captureToFile(outputPath: String): AppResult<CameraCaptureResult>
}
