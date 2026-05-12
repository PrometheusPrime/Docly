package com.docly.app.core.file

import android.content.Context
import com.docly.app.domain.model.DocumentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

interface AppFileDirectories {
    val doclyRootDirectory: File
    val rawScanDirectory: File
    val processedScanDirectory: File
    val thumbnailDirectory: File
    val pdfDirectory: File
    val txtDirectory: File
    val markdownDirectory: File
    val htmlDirectory: File
    val docxDirectory: File
    val xlsxDirectory: File
    val csvDirectory: File
    val imageDirectory: File
    val exportDirectory: File
    val tempDirectory: File
    val ocrDirectory: File

    fun documentDirectory(type: DocumentType): File

    fun ensureDirectories()
}

class AndroidAppFileDirectories @Inject constructor(@param:ApplicationContext private val context: Context) :
    AppFileDirectories {
    override val doclyRootDirectory: File
        get() = File(context.filesDir, "docly")

    override val rawScanDirectory: File
        get() = File(doclyRootDirectory, "scans/pages/raw")

    override val processedScanDirectory: File
        get() = File(doclyRootDirectory, "scans/pages/processed")

    override val thumbnailDirectory: File
        get() = File(doclyRootDirectory, "thumbnails")

    override val pdfDirectory: File
        get() = File(doclyRootDirectory, "documents/pdf")

    override val txtDirectory: File
        get() = File(doclyRootDirectory, "documents/txt")

    override val markdownDirectory: File
        get() = File(doclyRootDirectory, "documents/markdown")

    override val htmlDirectory: File
        get() = File(doclyRootDirectory, "documents/html")

    override val docxDirectory: File
        get() = File(doclyRootDirectory, "documents/docx")

    override val xlsxDirectory: File
        get() = File(doclyRootDirectory, "documents/xlsx")

    override val csvDirectory: File
        get() = File(doclyRootDirectory, "documents/csv")

    override val imageDirectory: File
        get() = File(doclyRootDirectory, "documents/images")

    override val exportDirectory: File
        get() = File(doclyRootDirectory, "exports")

    override val tempDirectory: File
        get() = File(doclyRootDirectory, "temp")

    override val ocrDirectory: File
        get() = File(doclyRootDirectory, "ocr")

    override fun documentDirectory(type: DocumentType): File = when (type) {
        DocumentType.PDF -> pdfDirectory
        DocumentType.TXT -> txtDirectory
        DocumentType.MARKDOWN -> markdownDirectory
        DocumentType.HTML -> htmlDirectory
        DocumentType.DOCX -> docxDirectory
        DocumentType.XLSX -> xlsxDirectory
        DocumentType.CSV -> csvDirectory
        DocumentType.IMAGE -> imageDirectory
        DocumentType.UNKNOWN -> tempDirectory
    }

    override fun ensureDirectories() {
        listOf(
            rawScanDirectory,
            processedScanDirectory,
            thumbnailDirectory,
            pdfDirectory,
            txtDirectory,
            markdownDirectory,
            htmlDirectory,
            docxDirectory,
            xlsxDirectory,
            csvDirectory,
            imageDirectory,
            exportDirectory,
            tempDirectory,
            ocrDirectory
        ).forEach { directory -> directory.mkdirs() }
    }
}
