package com.docly.app.feature.placeholder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.docly.app.ui.components.DoclyScreenScaffold

@Composable
fun PlaceholderScreen(title: String, message: String, onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    DoclyScreenScaffold(
        title = title,
        screenTestTag = "${title.lowercase()}_screen",
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
