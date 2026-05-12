package com.docly.app.core.reader

import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.TestDispatcherProvider
import com.docly.app.domain.model.FileRef
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderEnginesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun textReaderLoadsBoundedChunks() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = DefaultTextReaderEngine(TestDispatcherProvider(dispatcher))
        val file = temporaryFolder.newFile("long.txt").apply {
            writeText("abcdef", Charsets.UTF_8)
        }

        val first = engine.readChunk(FileRef.InternalFile(file.absolutePath), maxChars = 3).successData()
        assertEquals(listOf("abc"), first.lines)
        assertEquals(3L, first.nextOffset)
        assertTrue(first.hasMore)

        val second = engine.readChunk(FileRef.InternalFile(file.absolutePath), offset = 3L, maxChars = 3).successData()
        assertEquals(listOf("def"), second.lines)
        assertFalse(second.hasMore)
    }

    @Test
    fun markdownReaderRendersTablesAndEscapesRawHtml() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = DefaultMarkdownReaderEngine(TestDispatcherProvider(dispatcher))
        val file = temporaryFolder.newFile("notes.md").apply {
            writeText(
                """
                |A|B|
                |-|-|
                |1|2|

                <script>alert('x')</script>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val html = engine.render(FileRef.InternalFile(file.absolutePath)).successData().html

        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun docxReaderExtractsParagraphsListsAndTables() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = DefaultDocxReaderEngine(TestDispatcherProvider(dispatcher))
        val file = temporaryFolder.newFile("sample.docx")
        file.writeZip(
            "word/document.xml" to """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                      <w:r><w:t>Heading</w:t></w:r>
                    </w:p>
                    <w:p>
                      <w:pPr><w:numPr><w:ilvl w:val="0"/></w:numPr></w:pPr>
                      <w:r><w:t>List item</w:t></w:r>
                    </w:p>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
            """.trimIndent()
        )

        val document = engine.parse(FileRef.InternalFile(file.absolutePath)).successData()

        assertEquals(
            ExtractedDocumentBlock.Paragraph("Heading", ExtractedBlockStyle.HEADING),
            document.blocks[0]
        )
        assertEquals(
            ExtractedDocumentBlock.Paragraph("List item", ExtractedBlockStyle.LIST_ITEM),
            document.blocks[1]
        )
        assertEquals(ExtractedDocumentBlock.Table(listOf(listOf("A", "B"))), document.blocks[2])
    }

    @Test
    fun xlsxReaderLoadsSheetNamesAndRowPages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = DefaultXlsxReaderEngine(TestDispatcherProvider(dispatcher))
        val file = temporaryFolder.newFile("sample.xlsx")
        file.writeZip(
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Sheet One" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
            """.trimIndent(),
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent(),
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Name</t></si>
                  <si><t>Total</t></si>
                </sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                    <row r="2"><c r="A2"><v>42</v></c><c r="B2" t="b"><v>1</v></c></row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val workbook = engine.open(FileRef.InternalFile(file.absolutePath)).successData()
        val rows = engine.readRows(
            fileRef = FileRef.InternalFile(file.absolutePath),
            sheetIndex = 0,
            startRowIndex = 0,
            maxRows = 1
        ).successData()

        assertEquals(listOf(XlsxSheetInfo(name = "Sheet One", index = 0)), workbook.sheets)
        assertEquals(listOf(listOf("Name", "Total")), rows.rows)
        assertEquals(1, rows.nextRowIndex)
        assertTrue(rows.hasMore)
    }

    private fun File.writeZip(vararg entries: Pair<String, String>) {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun <T> AppResult<T>.successData(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> error(message)
    }
}
