package com.docly.app.core.reader

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class DefaultDocxReaderEngine @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    DocxReaderEngine {
    override suspend fun parse(fileRef: FileRef): AppResult<ExtractedDocument> = withContext(dispatcherProvider.io) {
        readerResult {
            val file = fileRef.requireInternalFile()
            if (file.length() > MAX_SIMPLIFIED_DOCX_BYTES) {
                throw ReaderFailure(
                    message = "This DOCX is too large for simplified reading in this phase.",
                    category = AppErrorCategory.VALIDATION
                )
            }
            ZipFile(file).use { zipFile ->
                val entry = zipFile.getEntry(DOCUMENT_XML_ENTRY)
                    ?: throw ReaderFailure("DOCX document content was not found.", AppErrorCategory.VALIDATION)
                val document = zipFile.getInputStream(entry).use(::parseXml)
                val body = document.documentElement.firstDescendantElement("body")
                    ?: throw ReaderFailure("DOCX document body was not found.", AppErrorCategory.VALIDATION)

                ExtractedDocument(
                    blocks = body.childElements()
                        .mapNotNull { child ->
                            when (child.localTagName) {
                                "p" -> child.toParagraphBlock()
                                "tbl" -> child.toTableBlock()
                                else -> null
                            }
                        }
                )
            }
        }
    }

    private fun Element.toParagraphBlock(): ExtractedDocumentBlock.Paragraph? {
        val text = paragraphText().trim()
        if (text.isBlank()) return null

        return ExtractedDocumentBlock.Paragraph(
            text = text,
            style = when {
                isHeadingParagraph() -> ExtractedBlockStyle.HEADING
                isListParagraph() -> ExtractedBlockStyle.LIST_ITEM
                else -> ExtractedBlockStyle.NORMAL
            }
        )
    }

    private fun Element.toTableBlock(): ExtractedDocumentBlock.Table? {
        val rows = childElements("tr").map { row ->
            row.childElements("tc").map { cell ->
                cell.childElements("p")
                    .joinToString(separator = "\n") { paragraph -> paragraph.paragraphText().trim() }
                    .trim()
            }
        }.filter { row -> row.any { cell -> cell.isNotBlank() } }

        return rows.takeIf { it.isNotEmpty() }?.let { ExtractedDocumentBlock.Table(it) }
    }

    private fun Element.paragraphText(): String {
        val builder = StringBuilder()
        visitDescendants { node ->
            when (node.localTagName) {
                "t" -> builder.append(node.textContent)
                "tab" -> builder.append('\t')
                "br" -> builder.append('\n')
            }
        }
        return builder.toString()
    }

    private fun Element.isHeadingParagraph(): Boolean {
        val style = firstDescendantElement("pStyle")?.attributeValue("val").orEmpty()
        return style.startsWith("Heading", ignoreCase = true) || style.equals("Title", ignoreCase = true)
    }

    private fun Element.isListParagraph(): Boolean = firstDescendantElement("numPr") != null

    private companion object {
        const val DOCUMENT_XML_ENTRY = "word/document.xml"
        const val MAX_SIMPLIFIED_DOCX_BYTES = 8L * 1024L * 1024L
    }
}

private val Node.localTagName: String
    get() = localName ?: nodeName.substringAfter(':')

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

private fun Element.firstDescendantElement(localName: String): Element? {
    var found: Element? = null
    visitDescendants { node ->
        if (found == null && node is Element && node.localTagName == localName) {
            found = node
        }
    }
    return found
}

private fun Element.childElements(localName: String? = null): List<Element> {
    val elements = mutableListOf<Element>()
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element && (localName == null || child.localTagName == localName)) {
            elements += child
        }
    }
    return elements
}

private fun Node.visitDescendants(block: (Node) -> Unit) {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        block(child)
        child.visitDescendants(block)
    }
}
