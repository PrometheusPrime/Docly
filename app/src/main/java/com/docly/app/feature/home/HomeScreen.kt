package com.docly.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.DoclyDocument
import com.docly.app.feature.library.LibraryUiState
import com.docly.app.ui.components.DoclyLazyScreenScaffold
import com.docly.app.ui.components.doclyMinimumTouchTarget

@Composable
fun HomeScreen(
    uiState: LibraryUiState,
    onOpenDocuments: () -> Unit,
    onStartScanner: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyLazyScreenScaffold(
        title = "Docly",
        screenTestTag = "home_screen",
        modifier = modifier
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Document workspace",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onOpenDocuments,
                        modifier = Modifier.weight(1f).doclyMinimumTouchTarget()
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text(text = "Documents")
                    }
                    OutlinedButton(
                        onClick = onStartScanner,
                        modifier = Modifier.weight(1f).doclyMinimumTouchTarget()
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Text(text = "Scan")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onOpenCreate,
                        modifier = Modifier.weight(1f).doclyMinimumTouchTarget()
                    ) {
                        Icon(imageVector = Icons.Filled.Create, contentDescription = null)
                        Text(text = "Create")
                    }
                    OutlinedButton(
                        onClick = onOpenTools,
                        modifier = Modifier.weight(1f).doclyMinimumTouchTarget()
                    ) {
                        Icon(imageVector = Icons.Filled.Create, contentDescription = null)
                        Text(text = "Convert")
                    }
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().doclyMinimumTouchTarget()
                ) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                    Text(text = "Settings")
                }
            }
        }
        item {
            RecentDocuments(documents = uiState.documents.take(3), onOpenDocuments = onOpenDocuments)
        }
    }
}

@Composable
private fun RecentDocuments(documents: List<DoclyDocument>, onOpenDocuments: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Recent documents",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (documents.isEmpty()) {
            Text(
                text = "Imported and scanned documents will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            documents.forEach { document ->
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onOpenDocuments, modifier = Modifier.doclyMinimumTouchTarget()) {
            Text(text = "View all")
        }
    }
}
