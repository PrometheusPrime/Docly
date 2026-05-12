package com.docly.app.domain

import com.docly.app.core.file.FileTypeResolver
import com.docly.app.domain.capability.DocumentCapabilityResolver
import com.docly.app.domain.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentFoundationTest {
    private val fileTypeResolver = FileTypeResolver()
    private val capabilityResolver = DocumentCapabilityResolver()

    @Test
    fun fileTypeResolverUsesMimeAndExtensionFallbacks() {
        assertEquals(DocumentType.PDF, fileTypeResolver.resolve("ignored.bin", "application/pdf"))
        assertEquals(DocumentType.TXT, fileTypeResolver.resolve("notes.txt", null))
        assertEquals(DocumentType.MARKDOWN, fileTypeResolver.resolve("readme.md", null))
        assertEquals(DocumentType.HTML, fileTypeResolver.resolve("index.htm", null))
        assertEquals(DocumentType.IMAGE, fileTypeResolver.resolve("scan.webp", null))
        assertEquals(DocumentType.DOCX, fileTypeResolver.resolve("paper.docx", null))
        assertEquals(DocumentType.XLSX, fileTypeResolver.resolve("table.xlsx", null))
        assertEquals(DocumentType.CSV, fileTypeResolver.resolve("rows.csv", null))
        assertEquals(DocumentType.UNKNOWN, fileTypeResolver.resolve("archive.zip", "application/zip"))
    }

    @Test
    fun capabilityResolverHidesUnsupportedActions() {
        val pdf = capabilityResolver.resolve(DocumentType.PDF)
        assertTrue(pdf.canView)
        assertFalse(pdf.canEdit)

        val markdown = capabilityResolver.resolve(DocumentType.MARKDOWN)
        assertTrue(markdown.canEdit)
        assertTrue(DocumentType.PDF in markdown.supportedOutputs)

        val docx = capabilityResolver.resolve(DocumentType.DOCX)
        assertTrue(docx.canView)
        assertFalse(docx.canEdit)
        assertTrue(docx.isSimplifiedView)

        val unknown = capabilityResolver.resolve(DocumentType.UNKNOWN)
        assertFalse(unknown.canView)
        assertEquals("Docly cannot open this file type yet.", unknown.limitationMessage)
    }
}
