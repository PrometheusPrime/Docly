package com.docly.app.data.local.mapper

import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.ScanSessionStatus

internal fun String.toScanMode(): ScanMode = enumValues<ScanMode>().firstOrNull { it.name == this } ?: ScanMode.DOCUMENT

internal fun String.toScanSessionStatus(): ScanSessionStatus = enumValues<ScanSessionStatus>().firstOrNull {
    it.name == this
} ?: ScanSessionStatus.ABANDONED
