package com.docly.app.data.converter

import com.docly.app.core.reader.DocxReaderEngine
import com.docly.app.core.reader.ExtractedBlockStyle
import com.docly.app.core.reader.ExtractedDocument
import com.docly.app.core.reader.ExtractedDocumentBlock
import com.docly.app.core.reader.WorkbookDocument
import com.docly.app.core.reader.XlsxReaderEngine
import com.docly.app.core.reader.XlsxRowPage
import com.docly.app.core.reader.XlsxSheetInfo
import com.docly.app.core.result.AppResult
import com.docly.app.core.testing.FakeHtmlToPdfExporter
import com.docly.app.core.testing.TestDispatcherProvider
import com.docly.app.domain.converter.ConverterOutput
import com.docly.app.domain.model.ConversionOptions
import com.docly.app.domain.model.ConversionRequest
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.repository.PdfRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterEnginesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = TestDispatcherProvider(dispatcher)

    @Test
    fun textEngineEscapesPlainTextWhenWritingHtml() = runTest(dispatcher) {
        val input = temporaryFolder.newFile("notes.txt").apply {
            writeText("A < B & C", Charsets.UTF_8)
        }
        val output = temporaryFolder.newFile("notes.html")
        val engine = TextDocumentConverterEngine(dispatcherProvider, FakeHtmlToPdfExporter())

        val result = engine.convert(
            request = request(DocumentType.TXT, DocumentType.HTML),
            sourceDocument = document(type = DocumentType.TXT, path = input.absolutePath),
            outputPath = output.absolutePath
        ).successData()

        assertEquals("text/html", result.mimeType)
        assertTrue(output.readText().contains("A &lt; B &amp; C"))
    }

    @Test
    fun markdownEngineWritesPlainTextWithoutMarkdownSyntax() = runTest(dispatcher) {
        val input = temporaryFolder.newFile("notes.md").apply {
            writeText("# Title\n\n- One\n- Two", Charsets.UTF_8)
        }
        val output = temporaryFolder.newFile("notes.txt")
        val engine = MarkdownDocumentConverterEngine(dispatcherProvider, FakeHtmlToPdfExporter())

        engine.convert(
            request = request(DocumentType.MARKDOWN, DocumentType.TXT),
            sourceDocument = document(type = DocumentType.MARKDOWN, path = input.absolutePath),
            outputPath = output.absolutePath
        ).successData()

        assertEquals("Title\n- One\n- Two\n", output.readText(Charsets.UTF_8))
    }

    @Test
    fun htmlEngineExtractsDecodedTextWithJsoup() = runTest(dispatcher) {
        val input = temporaryFolder.newFile("notes.html").apply {
            writeText("<p>One &amp; Two</p><strong>Bold</strong>", Charsets.UTF_8)
        }
        val output = temporaryFolder.newFile("notes.txt")
        val engine = HtmlDocumentConverterEngine(dispatcherProvider, FakeHtmlToPdfExporter())

        engine.convert(
            request = request(DocumentType.HTML, DocumentType.TXT),
            sourceDocument = document(type = DocumentType.HTML, path = input.absolutePath),
            outputPath = output.absolutePath
        ).successData()

        assertEquals("One & Two Bold\n", output.readText(Charsets.UTF_8))
    }

    @Test
    fun imageEngineDelegatesSingleImageToPdfRepository() = runTest(dispatcher) {
        val input = temporaryFolder.newFile("page.jpg").apply {
            writeText("image")
        }
        val output = temporaryFolder.newFile("page.pdf").apply { delete() }
        val pdfRepository = FakePdfRepository()
        val engine = ImageDocumentConverterEngine(pdfRepository)

        val result = engine.convert(
            request = request(DocumentType.IMAGE, DocumentType.PDF),
            sourceDocument = document(type = DocumentType.IMAGE, path = input.absolutePath),
            outputPath = output.absolutePath
        ).successData()

        assertEquals(listOf(input.absolutePath), pdfRepository.lastPageImagePaths)
        assertEquals("application/pdf", result.mimeType)
        assertEquals(1, result.pageCount)
        assertTrue(output.isFile)
    }

    @Test
    fun docxEngineWritesEscapedHtmlForParagraphsListsAndTables() = runTest(dispatcher) {
        val output = temporaryFolder.newFile("doc.html")
        val engine = DocxDocumentConverterEngine(FakeDocxReaderEngine())

        engine.convert(
            request = request(DocumentType.DOCX, DocumentType.HTML),
            sourceDocument = document(
                type = DocumentType.DOCX,
                path = temporaryFolder.newFile("doc.docx").absolutePath
            ),
            outputPath = output.absolutePath
        ).successData()

        val html = output.readText(Charsets.UTF_8)
        assertTrue(html.contains("<h2>Heading &lt;one&gt;</h2>"))
        assertTrue(html.contains("<ul><li>Item</li></ul>"))
        assertTrue(html.contains("<td>A</td>"))
    }

    @Test
    fun xlsxEngineStreamsRowsAndQuotesCsvCells() = runTest(dispatcher) {
        val output = temporaryFolder.newFile("sheet.csv")
        val engine = XlsxDocumentConverterEngine(FakeXlsxReaderEngine())

        engine.convert(
            request = request(
                inputType = DocumentType.XLSX,
                outputType = DocumentType.CSV,
                options = ConversionOptions(xlsxSheetIndex = 1)
            ),
            sourceDocument = document(
                type = DocumentType.XLSX,
                path = temporaryFolder.newFile("book.xlsx").absolutePath
            ),
            outputPath = output.absolutePath
        ).successData()

        assertEquals("Name,Total\n\"A, B\",\"He said \"\"yes\"\"\"\n", output.readText(Charsets.UTF_8))
    }

    private fun request(
        inputType: DocumentType,
        outputType: DocumentType,
        options: ConversionOptions = ConversionOptions()
    ): ConversionRequest = ConversionRequest(
        inputDocumentId = "document-id",
        inputType = inputType,
        outputType = outputType,
        outputFileName = "output.${outputType.name.lowercase()}",
        options = options
    )

    private fun document(type: DocumentType, path: String): DoclyDocument = DoclyDocument(
        id = "document-id",
        name = "Document",
        type = type,
        mimeType = null,
        fileRef = FileRef.InternalFile(path),
        source = DocumentSource.CREATED,
        fileSize = File(path).length(),
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun AppResult<ConverterOutput>.successData(): ConverterOutput = when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> error(message)
    }

    private class FakePdfRepository : PdfRepository {
        var lastPageImagePaths: List<String> = emptyList()

        override suspend fun createPdf(pageImagePaths: List<String>, outputPdfPath: String): AppResult<String> {
            lastPageImagePaths = pageImagePaths
            File(outputPdfPath).writeText("%PDF-1.4\n", Charsets.UTF_8)
            return AppResult.Success(outputPdfPath)
        }
    }

    private class FakeDocxReaderEngine : DocxReaderEngine {
        override suspend fun parse(fileRef: FileRef): AppResult<ExtractedDocument> = AppResult.Success(
            ExtractedDocument(
                blocks = listOf(
                    ExtractedDocumentBlock.Paragraph("Heading <one>", ExtractedBlockStyle.HEADING),
                    ExtractedDocumentBlock.Paragraph("Item", ExtractedBlockStyle.LIST_ITEM),
                    ExtractedDocumentBlock.Table(listOf(listOf("A", "B")))
                )
            )
        )
    }

    private class FakeXlsxReaderEngine : XlsxReaderEngine {
        override suspend fun open(fileRef: FileRef): AppResult<WorkbookDocument> = AppResult.Success(
            WorkbookDocument(listOf(XlsxSheetInfo("Sheet", 1)))
        )

        override suspend fun readRows(
            fileRef: FileRef,
            sheetIndex: Int,
            startRowIndex: Int,
            maxRows: Int
        ): AppResult<XlsxRowPage> {
            assertEquals(1, sheetIndex)
            return if (startRowIndex == 0) {
                AppResult.Success(XlsxRowPage(listOf(listOf("Name", "Total")), nextRowIndex = 1, hasMore = true))
            } else {
                AppResult.Success(
                    XlsxRowPage(
                        listOf(listOf("A, B", "He said \"yes\"")),
                        nextRowIndex = null,
                        hasMore = false
                    )
                )
            }
        }
    }
}
