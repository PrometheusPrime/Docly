package com.docly.app.core.reader

import com.docly.app.core.dispatchers.DispatcherProvider
import com.docly.app.core.result.AppErrorCategory
import com.docly.app.core.result.AppResult
import com.docly.app.domain.model.FileRef
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class DefaultTextReaderEngine @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    TextReaderEngine {
    override suspend fun readChunk(fileRef: FileRef, offset: Long, maxChars: Int): AppResult<TextReadChunk> =
        withContext(dispatcherProvider.io) {
            readerResult {
                val file = fileRef.requireInternalFile()
                if (maxChars <= 0) {
                    throw ReaderFailure("Text chunk size must be positive.", AppErrorCategory.VALIDATION)
                }

                file.inputStream().use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        var skipped = 0L
                        while (skipped < offset) {
                            val currentSkip = reader.skip(offset - skipped)
                            if (currentSkip <= 0L) break
                            skipped += currentSkip
                        }

                        val buffer = CharArray(maxChars + 1)
                        val readCount = reader.read(buffer)
                        if (readCount <= 0) {
                            return@readerResult TextReadChunk(
                                lines = emptyList(),
                                nextOffset = null,
                                hasMore = false
                            )
                        }

                        val hasMore = readCount > maxChars
                        val text = String(buffer, 0, minOf(readCount, maxChars))
                        TextReadChunk(
                            lines = text.normalizeLineEndings().split('\n'),
                            nextOffset = if (hasMore) offset + maxChars else null,
                            hasMore = hasMore
                        )
                    }
                }
            }
        }
}

class DefaultMarkdownReaderEngine @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    MarkdownReaderEngine {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder()
        .extensions(extensions)
        .build()
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    override suspend fun render(fileRef: FileRef): AppResult<RenderedHtmlDocument> =
        withContext(dispatcherProvider.io) {
            readerResult {
                val markdown = fileRef.requireInternalFile().readTextSafely()
                val html = htmlRenderer.render(parser.parse(markdown))
                RenderedHtmlDocument(html = html.wrapReaderHtml())
            }
        }
}

class DefaultHtmlReaderEngine @Inject constructor(private val dispatcherProvider: DispatcherProvider) :
    HtmlReaderEngine {
    override suspend fun read(fileRef: FileRef): AppResult<RenderedHtmlDocument> = withContext(dispatcherProvider.io) {
        readerResult {
            RenderedHtmlDocument(html = fileRef.requireInternalFile().readTextSafely().asReaderHtmlDocument())
        }
    }
}

private fun java.io.File.readTextSafely(): String {
    if (length() > MAX_RENDERED_TEXT_FILE_BYTES) {
        throw ReaderFailure(
            message = "This document is too large to render in the reader.",
            category = AppErrorCategory.VALIDATION
        )
    }
    return readText(Charsets.UTF_8)
}

private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

private fun String.asReaderHtmlDocument(): String = if (contains("<html", ignoreCase = true)) this else wrapReaderHtml()

private fun String.wrapReaderHtml(): String = """
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          body {
            font-family: sans-serif;
            color: #202124;
            background: #ffffff;
            margin: 0;
            padding: 18px;
            line-height: 1.5;
            overflow-wrap: anywhere;
          }
          table {
            border-collapse: collapse;
            width: 100%;
            margin: 12px 0;
          }
          th, td {
            border: 1px solid #d0d7de;
            padding: 6px 8px;
            vertical-align: top;
          }
          pre, code {
            background: #f6f8fa;
            border-radius: 4px;
          }
          pre {
            overflow-x: auto;
            padding: 10px;
          }
        </style>
      </head>
      <body>
        $this
      </body>
    </html>
""".trimIndent()

private const val MAX_RENDERED_TEXT_FILE_BYTES = 2L * 1024L * 1024L
