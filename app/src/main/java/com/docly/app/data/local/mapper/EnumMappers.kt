package com.docly.app.data.local.mapper

import com.docly.app.domain.model.ConversionStatus
import com.docly.app.domain.model.DiagnosticSeverity
import com.docly.app.domain.model.DiagnosticStage
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.model.PageReviewStatus
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionStatus

internal fun String.toDocumentType(): DocumentType = enumValues<DocumentType>().firstOrNull {
    it.name == this
} ?: DocumentType.UNKNOWN

internal fun String.toDocumentSource(): DocumentSource = enumValues<DocumentSource>().firstOrNull {
    it.name == this
} ?: DocumentSource.IMPORTED

internal fun String.toConversionStatus(): ConversionStatus = enumValues<ConversionStatus>().firstOrNull {
    it.name == this
} ?: ConversionStatus.FAILED

internal fun String.toScanMode(): ScanMode = enumValues<ScanMode>().firstOrNull { it.name == this } ?: ScanMode.DOCUMENT

internal fun String.toPageReviewStatus(): PageReviewStatus = enumValues<PageReviewStatus>().firstOrNull {
    it.name == this
} ?: PageReviewStatus.ACCEPTED

internal fun String.toScanSessionStatus(): ScanSessionStatus = enumValues<ScanSessionStatus>().firstOrNull {
    it.name == this
} ?: ScanSessionStatus.ABANDONED

internal fun String.toOcrStatus(): OcrStatus = enumValues<OcrStatus>().firstOrNull {
    it.name == this
} ?: OcrStatus.NOT_STARTED

internal fun String.toDiagnosticStage(): DiagnosticStage = enumValues<DiagnosticStage>().firstOrNull {
    it.name == this
} ?: DiagnosticStage.PROCESSING

internal fun String.toDiagnosticSeverity(): DiagnosticSeverity = enumValues<DiagnosticSeverity>().firstOrNull {
    it.name == this
} ?: DiagnosticSeverity.INFO
