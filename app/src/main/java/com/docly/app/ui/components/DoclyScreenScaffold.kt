package com.docly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DoclyScreenScaffold(
    title: String,
    screenTestTag: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    contentMaxWidth: Dp = DoclyLayoutDefaults.ContentMaxWidth,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(screenTestTag),
        topBar = {
            DoclyTopBar(
                title = title,
                onNavigateBack = onNavigateBack,
                actions = actions
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = doclyHorizontalPadding(), vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun DoclyLazyScreenScaffold(
    title: String,
    screenTestTag: String,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    lazyListTestTag: String? = null,
    contentMaxWidth: Dp = DoclyLayoutDefaults.WideContentMaxWidth,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(screenTestTag),
        topBar = {
            DoclyTopBar(
                title = title,
                onNavigateBack = onNavigateBack,
                actions = actions
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val listModifier = if (lazyListTestTag == null) {
                Modifier
            } else {
                Modifier.testTag(lazyListTestTag)
            }
            LazyColumn(
                modifier = listModifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = contentMaxWidth),
                contentPadding = PaddingValues(horizontal = doclyHorizontalPadding(), vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

private fun BoxWithConstraintsScope.doclyHorizontalPadding(): Dp = if (maxWidth < 360.dp) {
    DoclyLayoutDefaults.CompactHorizontalPadding
} else {
    DoclyLayoutDefaults.DefaultHorizontalPadding
}
