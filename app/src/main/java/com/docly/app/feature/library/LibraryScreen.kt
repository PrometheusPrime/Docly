package com.docly.app.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.domain.model.DocumentSource
import com.docly.app.domain.model.DocumentType
import com.docly.app.domain.model.FileRef
import com.docly.app.domain.model.OcrStatus
import com.docly.app.domain.model.SortMode
import com.docly.app.domain.model.ViewMode
import com.docly.app.ui.components.DoclyAdaptiveFourActionRow
import com.docly.app.ui.components.DoclyAdaptiveLayout
import com.docly.app.ui.components.DoclyEmptyContent
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyImageThumbnail
import com.docly.app.ui.components.DoclyLazyScreenScaffold
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onEvent: (LibraryUiEvent) -> Unit,
    onStartScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onEvent(LibraryUiEvent.OnImportDocumentSelected(uri.toString()))
        }
    }

    DoclyLazyScreenScaffold(
        title = "Documents",
        screenTestTag = DoclyTestTags.LIBRARY_SCREEN,
        modifier = modifier,
        lazyListTestTag = DoclyTestTags.LIBRARY_DOCUMENT_LIST,
        actions = {
            IconButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "text/html",
                            "text/markdown",
                            "image/*",
                            DOCX_MIME_TYPE,
                            XLSX_MIME_TYPE
                        )
                    )
                }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Import document")
            }
        }
    ) {
        item {
            DocumentsQuickActions(
                uiState = uiState,
                onStartScanner = onStartScanner,
                onImport = {
                    importLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "text/html",
                            "text/markdown",
                            "image/*",
                            DOCX_MIME_TYPE,
                            XLSX_MIME_TYPE
                        )
                    )
                }
            )
        }
        if (uiState.hasDocuments || uiState.hasActiveSearch || uiState.hasActiveFilter) {
            item {
                LibrarySearchAndControls(uiState = uiState, onEvent = onEvent)
            }
        }
        val showBlockingError = uiState.errorMessage != null && !uiState.hasDocuments
        if (uiState.errorMessage != null && !showBlockingError) {
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
                DoclyLoadingContent(message = "Loading documents...")
            }

            showBlockingError -> item {
                DoclyErrorContent(
                    title = "Could not load documents",
                    message = checkNotNull(uiState.errorMessage)
                )
            }

            !uiState.hasDocuments -> item {
                DoclyEmptyContent(
                    title = "No documents",
                    message = "Import a file or scan paper to start a local library.",
                    actionLabel = if (uiState.isImporting) "Importing..." else "Import document",
                    onAction = {
                        importLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/plain",
                                "text/html",
                                "text/markdown",
                                "image/*",
                                DOCX_MIME_TYPE,
                                XLSX_MIME_TYPE
                            )
                        )
                    }
                )
            }

            uiState.documents.isEmpty() -> item {
                DoclyEmptyContent(
                    title = "No matching documents",
                    message = "Clear search or filters to see more documents.",
                    actionLabel = "Clear search",
                    onAction = { onEvent(LibraryUiEvent.OnClearSearchClicked) }
                )
            }

            uiState.viewMode == ViewMode.GRID -> libraryDocumentGridItems(
                documents = uiState.documents,
                onEvent = onEvent
            )

            else -> libraryDocumentItems(documents = uiState.documents, onEvent = onEvent)
        }
    }

    RenameDocumentDialog(uiState = uiState, onEvent = onEvent)
    DeleteConfirmationDialog(uiState = uiState, onEvent = onEvent)
}

@Composable
private fun DocumentsQuickActions(uiState: LibraryUiState, onStartScanner: () -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Local documents",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        DoclyAdaptiveLayout {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onImport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.LIBRARY_IMPORT_ACTION)
                        .doclyMinimumTouchTarget()
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Text(text = if (uiState.isImporting) "Importing..." else "Import")
                }
                OutlinedButton(
                    onClick = onStartScanner,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DoclyTestTags.LIBRARY_SCANNER_ACTION)
                        .doclyMinimumTouchTarget()
                ) {
                    Text(text = "Scan")
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchAndControls(uiState: LibraryUiState, onEvent: (LibraryUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { value -> onEvent(LibraryUiEvent.OnSearchQueryChanged(value)) },
            label = { Text(text = "Search documents") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.LIBRARY_SEARCH_FIELD)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = uiState.favoritesOnly,
                onClick = { onEvent(LibraryUiEvent.OnFavoriteFilterToggled) },
                label = { Text(text = "Favorites") }
            )
            FilterChip(
                selected = uiState.typeFilter == DocumentType.PDF,
                onClick = { onEvent(LibraryUiEvent.OnTypeFilterChanged(toggleType(uiState, DocumentType.PDF))) },
                label = { Text(text = "PDF") }
            )
            FilterChip(
                selected = uiState.typeFilter == DocumentType.IMAGE,
                onClick = { onEvent(LibraryUiEvent.OnTypeFilterChanged(toggleType(uiState, DocumentType.IMAGE))) },
                label = { Text(text = "Images") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(
                onClick = {
                    onEvent(
                        LibraryUiEvent.OnSortModeChanged(
                            if (uiState.sortMode == SortMode.UPDATED_DESC) SortMode.NAME_ASC else SortMode.UPDATED_DESC
                        )
                    )
                },
                label = { Text(text = if (uiState.sortMode == SortMode.NAME_ASC) "Name" else "Recent") }
            )
            AssistChip(
                onClick = {
                    onEvent(
                        LibraryUiEvent.OnViewModeChanged(
                            if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        )
                    )
                },
                label = { Text(text = if (uiState.viewMode == ViewMode.LIST) "List" else "Grid") }
            )
            if (uiState.searchQuery.isNotBlank()) {
                OutlinedButton(
                    onClick = { onEvent(LibraryUiEvent.OnClearSearchClicked) },
                    modifier = Modifier
                        .testTag(DoclyTestTags.LIBRARY_CLEAR_SEARCH_ACTION)
                        .doclyMinimumTouchTarget()
                ) {
                    Text(text = "Clear")
                }
            }
        }
    }
}

private fun toggleType(uiState: LibraryUiState, documentType: DocumentType): DocumentType? =
    if (uiState.typeFilter == documentType) null else documentType

private fun LazyListScope.libraryDocumentItems(documents: List<DoclyDocument>, onEvent: (LibraryUiEvent) -> Unit) {
    items(
        items = documents,
        key = { document -> document.id }
    ) { document ->
        LibraryDocumentRow(document = document, onEvent = onEvent)
    }
}

private fun LazyListScope.libraryDocumentGridItems(documents: List<DoclyDocument>, onEvent: (LibraryUiEvent) -> Unit) {
    val rowCount = max(1, (documents.size + 1) / 2)
    items(rowCount) { rowIndex ->
        val first = documents.getOrNull(rowIndex * 2)
        val second = documents.getOrNull(rowIndex * 2 + 1)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (first != null) {
                Column(modifier = Modifier.weight(1f)) {
                    LibraryDocumentRow(document = first, onEvent = onEvent)
                }
            }
            if (second != null) {
                Column(modifier = Modifier.weight(1f)) {
                    LibraryDocumentRow(document = second, onEvent = onEvent)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun LibraryDocumentRow(document: DoclyDocument, onEvent: (LibraryUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.LIBRARY_DOCUMENT_ROW)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DoclyAdaptiveLayout {
            if (it) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryDocumentThumbnail(document = document)
                    LibraryDocumentDetails(document = document)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LibraryDocumentThumbnail(document = document)
                    LibraryDocumentDetails(document = document, modifier = Modifier.weight(1f))
                }
            }
        }
        DoclyAdaptiveFourActionRow(
            first = { actionModifier ->
                IconButton(
                    onClick = { onEvent(LibraryUiEvent.OnOpenDocumentClicked(document.id)) },
                    modifier = actionModifier.testTag(DoclyTestTags.LIBRARY_OPEN_ACTION)
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Open")
                }
            },
            second = { actionModifier ->
                IconButton(
                    onClick = { onEvent(LibraryUiEvent.OnShareDocumentClicked(document.id)) },
                    modifier = actionModifier.testTag(DoclyTestTags.LIBRARY_SHARE_ACTION)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = "Share")
                }
            },
            third = { actionModifier ->
                IconButton(
                    onClick = { onEvent(LibraryUiEvent.OnFavoriteDocumentClicked(document.id)) },
                    modifier = actionModifier.testTag(DoclyTestTags.LIBRARY_FAVORITE_ACTION)
                ) {
                    Icon(
                        imageVector = if (document.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (document.isFavorite) "Unfavorite" else "Favorite"
                    )
                }
            },
            fourth = { actionModifier ->
                Row(modifier = actionModifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { onEvent(LibraryUiEvent.OnEditDocumentClicked(document.id)) },
                        modifier = Modifier.testTag(DoclyTestTags.LIBRARY_EDIT_ACTION)
                    ) {
                        Icon(imageVector = Icons.Filled.Create, contentDescription = document.editActionLabel())
                    }
                    IconButton(onClick = { onEvent(LibraryUiEvent.OnRenameDocumentClicked(document.id)) }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Rename")
                    }
                    IconButton(
                        onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentClicked(document.id)) },
                        modifier = Modifier.testTag(DoclyTestTags.LIBRARY_DELETE_ACTION)
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        )
    }
}

private fun DoclyDocument.editActionLabel(): String = if (type == DocumentType.PDF) "Page tools" else "Edit"

@Composable
private fun LibraryDocumentThumbnail(document: DoclyDocument) {
    DoclyImageThumbnail(
        imagePath = document.thumbnailPath,
        contentDescription = "${document.name} thumbnail",
        modifier = Modifier.size(width = 72.dp, height = 96.dp),
        testTag = DoclyTestTags.LIBRARY_DOCUMENT_THUMBNAIL
    )
}

@Composable
private fun LibraryDocumentDetails(document: DoclyDocument, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Text(
            text = document.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "${document.type.label} - ${document.source.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${document.fileSize.toReadableSize()} - Updated ${document.updatedAt.formattedDate()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (document.isFavorite) {
            Text(
                text = "Favorite",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RenameDocumentDialog(uiState: LibraryUiState, onEvent: (LibraryUiEvent) -> Unit) {
    val document = uiState.pendingRenameDocument ?: return

    AlertDialog(
        onDismissRequest = {
            if (!uiState.isRenaming) {
                onEvent(LibraryUiEvent.OnRenameDocumentDismissed)
            }
        },
        title = { Text(text = "Rename document") },
        text = {
            OutlinedTextField(
                value = uiState.pendingRenameName,
                onValueChange = { value -> onEvent(LibraryUiEvent.OnRenameDocumentNameChanged(value)) },
                label = { Text(text = "Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onEvent(LibraryUiEvent.OnRenameDocumentConfirmed) },
                enabled = !uiState.isRenaming && uiState.pendingRenameName.isNotBlank(),
                modifier = Modifier.doclyMinimumTouchTarget()
            ) {
                Text(text = if (uiState.isRenaming) "Renaming..." else "Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(LibraryUiEvent.OnRenameDocumentDismissed) },
                enabled = !uiState.isRenaming,
                modifier = Modifier.doclyMinimumTouchTarget()
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier.semantics {
            contentDescription = "Rename ${document.name}"
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(uiState: LibraryUiState, onEvent: (LibraryUiEvent) -> Unit) {
    val document = uiState.pendingDeleteDocument ?: return

    AlertDialog(
        onDismissRequest = {
            if (!uiState.isDeleting) {
                onEvent(LibraryUiEvent.OnDeleteDocumentDismissed)
            }
        },
        title = { Text(text = "Delete document?") },
        text = {
            Text(text = "Delete \"${document.name}\" from Docly? This removes the app-managed copy.")
        },
        confirmButton = {
            Button(
                onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentConfirmed) },
                enabled = !uiState.isDeleting,
                modifier = Modifier
                    .testTag(DoclyTestTags.LIBRARY_DELETE_CONFIRM_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = if (uiState.isDeleting) "Deleting..." else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(LibraryUiEvent.OnDeleteDocumentDismissed) },
                enabled = !uiState.isDeleting,
                modifier = Modifier
                    .testTag(DoclyTestTags.LIBRARY_DELETE_DISMISS_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "Cancel")
            }
        },
        modifier = Modifier
            .testTag(DoclyTestTags.LIBRARY_DELETE_DIALOG)
            .semantics {
                contentDescription = "Delete document confirmation"
            }
    )
}

private val DocumentType.label: String
    get() = when (this) {
        DocumentType.PDF -> "PDF"
        DocumentType.TXT -> "TXT"
        DocumentType.MARKDOWN -> "Markdown"
        DocumentType.HTML -> "HTML"
        DocumentType.DOCX -> "DOCX"
        DocumentType.XLSX -> "XLSX"
        DocumentType.CSV -> "CSV"
        DocumentType.IMAGE -> "Image"
        DocumentType.UNKNOWN -> "Unknown"
    }

private val DocumentSource.label: String
    get() = when (this) {
        DocumentSource.INTERNAL -> "Internal"
        DocumentSource.EXTERNAL_URI -> "External"
        DocumentSource.SCANNED -> "Scanned"
        DocumentSource.IMPORTED -> "Imported"
        DocumentSource.CREATED -> "Created"
        DocumentSource.CONVERTED -> "Converted"
    }

private fun Long.formattedDate(): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(this))

private fun Long.toReadableSize(): String = when {
    this <= 0L -> "Unknown size"
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${this / (1024L * 1024L)} MB"
}

private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    DoclyTheme {
        LibraryScreen(
            uiState = LibraryUiState(
                totalDocumentCount = 1,
                documents = listOf(
                    DoclyDocument(
                        id = "document-id",
                        name = "Math Paper",
                        type = DocumentType.PDF,
                        mimeType = "application/pdf",
                        fileRef = FileRef.InternalFile("/pdf/math.pdf"),
                        source = DocumentSource.SCANNED,
                        fileSize = 250_000L,
                        pageCount = 2,
                        createdAt = 1L,
                        updatedAt = 1L,
                        ocrStatus = OcrStatus.NOT_STARTED
                    )
                )
            ),
            onEvent = {},
            onStartScanner = {}
        )
    }
}
