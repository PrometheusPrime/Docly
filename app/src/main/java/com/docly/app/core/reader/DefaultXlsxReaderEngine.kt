package com.docly.app.core.reader

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

class DefaultXlsxReaderEngine @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    XlsxReaderEngine {
    override suspend fun open(fileRef: FileRef): AppResult<WorkbookDocument> = withContext(dispatcherProvider.io) {
        readerResult {
            val file = fileRef.requireInternalFile()
            if (file.length() > MAX_SIMPLIFIED_XLSX_BYTES) {
                throw ReaderFailure(
                    message = "This XLSX is too large for simplified reading in this phase.",
                    category = AppErrorCategory.VALIDATION
                )
            }
            ZipFile(file).use { zipFile ->
                WorkbookDocument(
                    sheets = zipFile.readSheetRefs().mapIndexed { index, sheet ->
                        XlsxSheetInfo(name = sheet.name, index = index)
                    }
                )
            }
        }
    }

    override suspend fun readRows(
        fileRef: FileRef,
        sheetIndex: Int,
        startRowIndex: Int,
        maxRows: Int
    ): AppResult<XlsxRowPage> = withContext(dispatcherProvider.io) {
        readerResult {
            if (sheetIndex < 0 || startRowIndex < 0 || maxRows <= 0) {
                throw ReaderFailure("Invalid spreadsheet range.", AppErrorCategory.VALIDATION)
            }

            val file = fileRef.requireInternalFile()
            if (file.length() > MAX_SIMPLIFIED_XLSX_BYTES) {
                throw ReaderFailure(
                    message = "This XLSX is too large for simplified reading in this phase.",
                    category = AppErrorCategory.VALIDATION
                )
            }
            ZipFile(file).use { zipFile ->
                val sheets = zipFile.readSheetRefs()
                val sheet = sheets.getOrNull(sheetIndex)
                    ?: throw ReaderFailure("Spreadsheet sheet was not found.", AppErrorCategory.VALIDATION)
                val sheetEntry = zipFile.getEntry(sheet.path)
                    ?: throw ReaderFailure("Spreadsheet sheet content was not found.", AppErrorCategory.VALIDATION)
                val sharedStrings = zipFile.readSharedStrings()
                val handler = XlsxSheetRowsHandler(
                    sharedStrings = sharedStrings,
                    startRowIndex = startRowIndex,
                    maxRows = maxRows
                )
                try {
                    zipFile.getInputStream(sheetEntry).use { input ->
                        secureSaxParserFactory().newSAXParser().parse(input, handler)
                    }
                } catch (stop: StopSheetParsing) {
                    // We intentionally stop after collecting one page plus one row.
                }
                XlsxRowPage(
                    rows = handler.rows,
                    nextRowIndex = if (handler.hasMore) startRowIndex + handler.rows.size else null,
                    hasMore = handler.hasMore
                )
            }
        }
    }
}

private data class XlsxSheetRef(val name: String, val path: String)

private fun ZipFile.readSheetRefs(): List<XlsxSheetRef> {
    val workbookEntry = getEntry(WORKBOOK_ENTRY)
        ?: throw ReaderFailure("Spreadsheet workbook was not found.", AppErrorCategory.VALIDATION)
    val rels = readWorkbookRelationships()
    val workbook = getInputStream(workbookEntry).use(::parseXml)
    val sheetNodes = workbook.documentElement.elementsByTagName("sheet")
    return sheetNodes.mapNotNull { sheet ->
        val name = sheet.attributeValue("name")?.ifBlank { null } ?: "Sheet"
        val relationshipId = sheet.attributeValue("id") ?: return@mapNotNull null
        val path = rels[relationshipId] ?: return@mapNotNull null
        XlsxSheetRef(name = name, path = path)
    }
}

private fun ZipFile.readWorkbookRelationships(): Map<String, String> {
    val relsEntry = getEntry(WORKBOOK_RELS_ENTRY) ?: return emptyMap()
    val rels = getInputStream(relsEntry).use(::parseXml)
    return rels.documentElement.elementsByTagName("Relationship").mapNotNull { relationship ->
        val id = relationship.attributeValue("Id") ?: return@mapNotNull null
        val target = relationship.attributeValue("Target") ?: return@mapNotNull null
        id to target.toWorkbookPartPath()
    }.toMap()
}

private fun ZipFile.readSharedStrings(): List<String> {
    val entry = getEntry(SHARED_STRINGS_ENTRY) ?: return emptyList()
    val document = getInputStream(entry).use(::parseXml)
    return document.documentElement.elementsByTagName("si").map { sharedString ->
        sharedString.elementsByTagName("t")
            .joinToString(separator = "") { textElement -> textElement.textContent.orEmpty() }
    }
}

private class XlsxSheetRowsHandler(
    private val sharedStrings: List<String>,
    private val startRowIndex: Int,
    private val maxRows: Int
) : DefaultHandler() {
    val rows = mutableListOf<List<String>>()
    var hasMore: Boolean = false
        private set

    private var rowIndex = -1
    private var collectingRow = false
    private var currentRow = mutableListOf<String>()
    private var currentCellType: String? = null
    private var currentCellColumnIndex: Int? = null
    private var currentValue: String = ""
    private var captureText = false
    private val textBuffer = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        when (localName ?: qName) {
            "row" -> {
                rowIndex += 1
                collectingRow = rowIndex >= startRowIndex
                currentRow = mutableListOf()
            }

            "c" -> {
                if (collectingRow) {
                    currentCellType = attributes.getValue("t")
                    currentCellColumnIndex = attributes.getValue("r")?.toColumnIndex()
                    currentValue = ""
                }
            }

            "v", "t" -> {
                if (collectingRow) {
                    captureText = true
                    textBuffer.clear()
                }
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (captureText) {
            textBuffer.append(ch, start, length)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (localName ?: qName) {
            "v", "t" -> {
                if (collectingRow && captureText) {
                    currentValue = textBuffer.toString()
                    captureText = false
                }
            }

            "c" -> {
                if (collectingRow) {
                    val columnIndex = currentCellColumnIndex ?: currentRow.size
                    while (currentRow.size < columnIndex) {
                        currentRow += ""
                    }
                    currentRow += currentValue.resolveCellValue(currentCellType, sharedStrings)
                }
            }

            "row" -> {
                if (collectingRow) {
                    if (rows.size >= maxRows) {
                        hasMore = true
                        throw StopSheetParsing()
                    }
                    rows += currentRow.trimTrailingEmptyCells()
                }
                collectingRow = false
            }
        }
    }
}

private class StopSheetParsing : SAXException()

private fun String.resolveCellValue(cellType: String?, sharedStrings: List<String>): String = when (cellType) {
    "s" -> toIntOrNull()?.let { index -> sharedStrings.getOrNull(index) }.orEmpty()
    "b" -> if (this == "1") "TRUE" else "FALSE"
    else -> this
}

private fun MutableList<String>.trimTrailingEmptyCells(): List<String> {
    var endExclusive = size
    while (endExclusive > 0 && this[endExclusive - 1].isBlank()) {
        endExclusive -= 1
    }
    return take(endExclusive)
}

private fun String.toColumnIndex(): Int {
    val letters = takeWhile { it.isLetter() }.uppercase()
    if (letters.isBlank()) return 0
    var index = 0
    for (letter in letters) {
        index = index * 26 + (letter - 'A' + 1)
    }
    return index - 1
}

private fun String.toWorkbookPartPath(): String {
    val normalized = replace('\\', '/')
    return when {
        normalized.startsWith("/") -> normalized.trimStart('/')
        normalized.startsWith("xl/") -> normalized
        else -> "xl/$normalized"
    }
}

private fun Element.elementsByTagName(localName: String): List<Element> {
    val nodes = getElementsByTagNameNS("*", localName)
    val result = mutableListOf<Element>()
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node is Element) result += node
    }
    if (result.isNotEmpty()) return result

    val fallbackNodes = getElementsByTagName(localName)
    for (index in 0 until fallbackNodes.length) {
        val node = fallbackNodes.item(index)
        if (node is Element) result += node
    }
    return result
}

private fun Element.attributeValue(localName: String): String? {
    if (hasAttribute(localName)) return getAttribute(localName)
    val attributes = attributes
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index)
        if ((attribute.localName ?: attribute.nodeName.substringAfter(':')) == localName) {
            return attribute.nodeValue
        }
    }
    return null
}

private const val WORKBOOK_ENTRY = "xl/workbook.xml"
private const val WORKBOOK_RELS_ENTRY = "xl/_rels/workbook.xml.rels"
private const val SHARED_STRINGS_ENTRY = "xl/sharedStrings.xml"
private const val MAX_SIMPLIFIED_XLSX_BYTES = 8L * 1024L * 1024L
