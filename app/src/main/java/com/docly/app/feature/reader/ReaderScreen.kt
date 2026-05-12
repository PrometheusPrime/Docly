package com.docly.app.feature.reader

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.docly.app.core.reader.ExtractedBlockStyle
import com.docly.app.core.reader.ExtractedDocumentBlock
import com.docly.app.domain.model.DocumentType
import com.docly.app.ui.components.DoclyAdaptiveFourActionRow
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLazyScreenScaffold
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import kotlin.math.max

@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onEvent: (ReaderUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEvent(ReaderUiEvent.OnStart)
    }

    DoclyLazyScreenScaffold(
        title = uiState.title,
        screenTestTag = DoclyTestTags.READER_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack,
        contentMaxWidth = 960.dp
    ) {
        if (uiState.errorMessage != null && uiState.content != null) {
            item {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when {
            uiState.isLoading -> item {
                DoclyLoadingContent(message = "Opening document...")
            }

            uiState.errorMessage != null && uiState.content == null -> item {
                DoclyErrorContent(
                    title = "Could not open document",
                    message = uiState.errorMessage,
                    actionLabel = "Retry",
                    onAction = { onEvent(ReaderUiEvent.OnRetryClicked) }
                )
            }

            uiState.content == null -> item {
                DoclyEmptyContent(
                    title = "Nothing to show",
                    message = "This document has no readable content."
                )
            }

            else -> readerContentItems(uiState = uiState, onEvent = onEvent)
        }
    }
}

private fun LazyListScope.readerContentItems(uiState: ReaderUiState, onEvent: (ReaderUiEvent) -> Unit) {
    when (val content = checkNotNull(uiState.content)) {
        is ReaderContent.Pdf -> pdfContentItems(uiState = uiState, content = content, onEvent = onEvent)
        is ReaderContent.Text -> textContentItems(uiState = uiState, content = content, onEvent = onEvent)
        is ReaderContent.Web -> webContentItems(content = content)
        is ReaderContent.Docx -> docxContentItems(content = content)
        is ReaderContent.Xlsx -> xlsxContentItems(uiState = uiState, content = content, onEvent = onEvent)
    }
}

private fun LazyListScope.pdfContentItems(
    uiState: ReaderUiState,
    content: ReaderContent.Pdf,
    onEvent: (ReaderUiEvent) -> Unit
) {
    item {
        Text(
            text = "Page ${content.currentPageIndex + 1} of ${content.pageCount}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    item {
        DoclyAdaptiveFourActionRow(
            first = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ReaderUiEvent.OnPdfPreviousPageClicked) },
                    enabled = uiState.canNavigatePdfPrevious && !uiState.isLoadingMore,
                    modifier = actionModifier.testTag(DoclyTestTags.READER_PDF_PREVIOUS_ACTION)
                ) {
                    Text(text = "Previous")
                }
            },
            second = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ReaderUiEvent.OnPdfNextPageClicked) },
                    enabled = uiState.canNavigatePdfNext && !uiState.isLoadingMore,
                    modifier = actionModifier.testTag(DoclyTestTags.READER_PDF_NEXT_ACTION)
                ) {
                    Text(text = "Next")
                }
            },
            third = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ReaderUiEvent.OnPdfZoomOutClicked) },
                    enabled = !uiState.isLoadingMore,
                    modifier = actionModifier.testTag(DoclyTestTags.READER_PDF_ZOOM_OUT_ACTION)
                ) {
                    Text(text = "-")
                }
            },
            fourth = { actionModifier ->
                OutlinedButton(
                    onClick = { onEvent(ReaderUiEvent.OnPdfZoomInClicked) },
                    enabled = !uiState.isLoadingMore,
                    modifier = actionModifier.testTag(DoclyTestTags.READER_PDF_ZOOM_IN_ACTION)
                ) {
                    Text(text = "+")
                }
            }
        )
    }
    if (uiState.isLoadingMore) {
        item {
            DoclyLoadingContent(message = "Rendering page...")
        }
    }
    item {
        PdfPage(content = content, onTargetWidthChanged = { widthPx ->
            onEvent(ReaderUiEvent.OnPdfTargetWidthChanged(widthPx))
        })
    }
}

@Composable
private fun PdfPage(content: ReaderContent.Pdf, onTargetWidthChanged: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx().toInt() }
        LaunchedEffect(widthPx) {
            onTargetWidthChanged(widthPx)
        }

        val aspectRatio = if (content.renderedWidth > 0 && content.renderedHeight > 0) {
            content.renderedWidth.toFloat() / content.renderedHeight.toFloat()
        } else {
            0.707f
        }
        DoclyImageThumbnail(
            imagePath = content.renderedPagePath,
            contentDescription = "PDF page ${content.currentPageIndex + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 900.dp)
                .aspectRatio(aspectRatio)
                .testTag(DoclyTestTags.READER_PDF_PAGE),
            contentScale = ContentScale.Fit
        )
    }
}

private fun LazyListScope.textContentItems(
    uiState: ReaderUiState,
    content: ReaderContent.Text,
    onEvent: (ReaderUiEvent) -> Unit
) {
    item {
        ReaderTextControls(uiState = uiState, onEvent = onEvent)
    }
    items(content.lines) { line ->
        ReaderTextLine(line = line, uiState = uiState)
    }
    if (content.hasMore) {
        item {
            Button(
                onClick = { onEvent(ReaderUiEvent.OnLoadMoreTextClicked) },
                enabled = !uiState.isLoadingMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.READER_LOAD_MORE_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = if (uiState.isLoadingMore) "Loading..." else "Load more")
            }
        }
    }
}

@Composable
private fun ReaderTextControls(uiState: ReaderUiState, onEvent: (ReaderUiEvent) -> Unit) {
    DoclyAdaptiveFourActionRow(
        first = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReaderUiEvent.OnTextSizeDecreaseClicked) },
                modifier = actionModifier.testTag(DoclyTestTags.READER_TEXT_SIZE_DECREASE_ACTION)
            ) {
                Text(text = "A-")
            }
        },
        second = { actionModifier ->
            OutlinedButton(
                onClick = { onEvent(ReaderUiEvent.OnTextSizeIncreaseClicked) },
                modifier = actionModifier.testTag(DoclyTestTags.READER_TEXT_SIZE_INCREASE_ACTION)
            ) {
                Text(text = "A+")
            }
        },
        third = { actionModifier ->
            AssistChip(
                onClick = { onEvent(ReaderUiEvent.OnReaderThemeToggled) },
                label = { Text(text = if (uiState.useDarkReaderTheme) "Dark" else "Light") },
                modifier = actionModifier.testTag(DoclyTestTags.READER_THEME_TOGGLE_ACTION)
            )
        },
        fourth = { actionModifier ->
            Text(
                text = "${uiState.textSizeSp.toInt()}sp",
                style = MaterialTheme.typography.bodyMedium,
                modifier = actionModifier.padding(vertical = 12.dp)
            )
        }
    )
}

@Composable
private fun ReaderTextLine(line: String, uiState: ReaderUiState) {
    val background = if (uiState.useDarkReaderTheme) Color(0xFF151515) else MaterialTheme.colorScheme.background
    val foreground = if (uiState.useDarkReaderTheme) Color.White else MaterialTheme.colorScheme.onBackground
    Surface(
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.READER_TEXT_CONTENT)
    ) {
        Text(
            text = line.ifEmpty { " " },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = uiState.textSizeSp.sp),
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private fun LazyListScope.webContentItems(content: ReaderContent.Web) {
    content.simplifiedMessage?.let { message ->
        item {
            SimplifiedBanner(message = message)
        }
    }
    item {
        ReaderWebView(html = content.html)
    }
}

@Composable
private fun ReaderWebView(html: String) {
    var webView: WebView? = null
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
                }
            }
        },
        update = { view ->
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 520.dp)
            .testTag(DoclyTestTags.READER_WEB_CONTENT)
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }
}

private fun LazyListScope.docxContentItems(content: ReaderContent.Docx) {
    item {
        SimplifiedBanner(message = content.simplifiedMessage)
    }
    if (content.blocks.isEmpty()) {
        item {
            DoclyEmptyContent(title = "No readable content", message = "This DOCX has no simplified text blocks.")
        }
    } else {
        items(content.blocks) { block ->
            DocxBlock(block = block)
        }
    }
}

@Composable
private fun SimplifiedBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.READER_SIMPLIFIED_BANNER)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun DocxBlock(block: ExtractedDocumentBlock) {
    when (block) {
        is ExtractedDocumentBlock.Paragraph -> {
            val style = when (block.style) {
                ExtractedBlockStyle.HEADING -> MaterialTheme.typography.titleMedium
                ExtractedBlockStyle.LIST_ITEM -> MaterialTheme.typography.bodyLarge
                ExtractedBlockStyle.NORMAL -> MaterialTheme.typography.bodyLarge
            }
            val prefix = if (block.style == ExtractedBlockStyle.LIST_ITEM) "- " else ""
            Text(
                text = "$prefix${block.text}",
                style = style,
                fontWeight = if (block.style == ExtractedBlockStyle.HEADING) FontWeight.SemiBold else null,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.READER_DOCX_CONTENT)
            )
        }

        is ExtractedDocumentBlock.Table -> ReaderTable(
            rows = block.rows,
            modifier = Modifier.testTag(DoclyTestTags.READER_DOCX_CONTENT)
        )
    }
}

private fun LazyListScope.xlsxContentItems(
    uiState: ReaderUiState,
    content: ReaderContent.Xlsx,
    onEvent: (ReaderUiEvent) -> Unit
) {
    item {
        SimplifiedBanner(message = content.simplifiedMessage)
    }
    if (content.sheets.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag(DoclyTestTags.READER_XLSX_SHEET_TABS),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content.sheets.forEach { sheet ->
                    val selected = sheet.index == content.selectedSheetIndex
                    if (selected) {
                        Button(onClick = {}, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(text = sheet.name)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onEvent(ReaderUiEvent.OnXlsxSheetSelected(sheet.index)) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(text = sheet.name)
                        }
                    }
                }
            }
        }
    }
    if (content.rows.isEmpty()) {
        item {
            DoclyEmptyContent(title = "No rows", message = "This sheet has no simplified table rows.")
        }
    } else {
        item {
            ReaderTable(rows = content.rows, modifier = Modifier.testTag(DoclyTestTags.READER_XLSX_TABLE))
        }
    }
    if (content.hasMore) {
        item {
            Button(
                onClick = { onEvent(ReaderUiEvent.OnLoadMoreRowsClicked) },
                enabled = !uiState.isLoadingMore,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DoclyTestTags.READER_LOAD_MORE_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = if (uiState.isLoadingMore) "Loading..." else "Load more rows")
            }
        }
    }
}

@Composable
private fun ReaderTable(rows: List<List<String>>, modifier: Modifier = Modifier) {
    val columnCount = max(1, rows.maxOfOrNull { row -> row.size } ?: 1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.widthIn(min = (columnCount * 96).dp)) {
                repeat(columnCount) { columnIndex ->
                    val value = row.getOrNull(columnIndex).orEmpty()
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .background(
                                if (rowIndex == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.background
                                }
                            )
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    DoclyTheme {
        ReaderScreen(
            uiState = ReaderUiState(
                documentId = "document-id",
                title = "Notes",
                documentType = DocumentType.TXT,
                content = ReaderContent.Text(
                    lines = listOf("First line", "Second line"),
                    nextOffset = null,
                    hasMore = false
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
