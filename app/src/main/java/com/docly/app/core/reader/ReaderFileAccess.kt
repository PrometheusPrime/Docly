package com.docly.app.core.reader

import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import java.io.File
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource

internal fun FileRef.requireInternalFile(): File = when (this) {
    is FileRef.InternalFile -> File(path).takeIf { file -> file.isFile }
        ?: throw ReaderFailure("Document file not found.", AppErrorCategory.STORAGE)

    is FileRef.ExternalUri -> throw ReaderFailure(
        message = "External document references must be imported before reading.",
        category = AppErrorCategory.STORAGE
    )
}

internal class ReaderFailure(
    override val message: String,
    val category: AppErrorCategory,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

internal suspend fun <T> readerResult(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (failure: ReaderFailure) {
    AppResult.Error(
        message = failure.message,
        category = failure.category,
        throwable = failure.cause
    )
} catch (throwable: Throwable) {
    AppResult.Error(
        message = throwable.message ?: "Could not read this document.",
        category = AppErrorCategory.STORAGE,
        throwable = throwable
    )
}

internal fun secureDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    isExpandEntityReferences = false
    setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
    setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
}

internal fun secureSaxParserFactory(): SAXParserFactory = SAXParserFactory.newInstance().apply {
    isNamespaceAware = true
    setFeatureSafely(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
    setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
}

internal fun parseXml(inputStream: InputStream): org.w3c.dom.Document = secureDocumentBuilderFactory()
    .newDocumentBuilder()
    .parse(InputSource(inputStream))

private fun DocumentBuilderFactory.setFeatureSafely(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

private fun SAXParserFactory.setFeatureSafely(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}
