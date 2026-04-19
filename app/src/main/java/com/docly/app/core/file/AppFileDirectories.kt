package com.docly.app.core.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface AppFileDirectories {
    val rawScanDirectory: File
    val processedScanDirectory: File
    val thumbnailDirectory: File
    val pdfDirectory: File

    fun ensureDirectories()
}

class AndroidAppFileDirectories @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppFileDirectories {
    override val rawScanDirectory: File
        get() = File(context.filesDir, "scans/raw")

    override val processedScanDirectory: File
        get() = File(context.filesDir, "scans/processed")

    override val thumbnailDirectory: File
        get() = File(context.filesDir, "scans/thumbnails")

    override val pdfDirectory: File
        get() = File(context.filesDir, "documents/pdf")

    override fun ensureDirectories() {
        rawScanDirectory.mkdirs()
        processedScanDirectory.mkdirs()
        thumbnailDirectory.mkdirs()
        pdfDirectory.mkdirs()
    }
}
