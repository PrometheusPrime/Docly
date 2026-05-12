package com.docly.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object DoclyLayoutDefaults {
    val CompactHorizontalPadding = 16.dp
    val DefaultHorizontalPadding = 24.dp
    val ContentMaxWidth = 720.dp
    val WideContentMaxWidth = 920.dp
    val MinimumTouchTarget = 48.dp
    val CompactActionWidth = 420.dp
    const val LARGE_FONT_SCALE = 1.5f
}

@Composable
fun DoclyAdaptiveLayout(
    modifier: Modifier = Modifier,
    compactWidth: Dp = DoclyLayoutDefaults.CompactActionWidth,
    content: @Composable (isStacked: Boolean) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isStacked =
            maxWidth < compactWidth || LocalDensity.current.fontScale >= DoclyLayoutDefaults.LARGE_FONT_SCALE
        content(isStacked)
    }
}

@Composable
fun DoclyAdaptiveTwoActionRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyAdaptiveLayout(modifier = modifier) { isStacked ->
        if (isStacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                second(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                first(Modifier.weight(1f).doclyMinimumTouchTarget())
                second(Modifier.weight(1f).doclyMinimumTouchTarget())
            }
        }
    }
}

@Composable
fun DoclyAdaptiveThreeActionRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    third: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyAdaptiveLayout(modifier = modifier) { isStacked ->
        if (isStacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                second(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                third(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.weight(1f).doclyMinimumTouchTarget())
                second(Modifier.weight(1f).doclyMinimumTouchTarget())
                third(Modifier.weight(1f).doclyMinimumTouchTarget())
            }
        }
    }
}

@Composable
fun DoclyAdaptiveFourActionRow(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    third: @Composable (Modifier) -> Unit,
    fourth: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyAdaptiveLayout(modifier = modifier) { isStacked ->
        if (isStacked) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                first(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                second(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                third(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
                fourth(Modifier.fillMaxWidth().doclyMinimumTouchTarget())
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    first(Modifier.weight(1f).doclyMinimumTouchTarget())
                    second(Modifier.weight(1f).doclyMinimumTouchTarget())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    third(Modifier.weight(1f).doclyMinimumTouchTarget())
                    fourth(Modifier.weight(1f).doclyMinimumTouchTarget())
                }
            }
        }
    }
}

fun Modifier.doclyMinimumTouchTarget(): Modifier = heightIn(min = DoclyLayoutDefaults.MinimumTouchTarget)
