package com.docly.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.docly.app.domain.model.ScanMode
import com.docly.app.ui.util.DoclyTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanModeSelector(
    selectedScanMode: ScanMode,
    onScanModeSelected: (ScanMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DoclyTestTags.SCAN_MODE_SELECTOR)
    ) {
        ScanMode.entries.forEachIndexed { index, scanMode ->
            SegmentedButton(
                selected = scanMode == selectedScanMode,
                onClick = { onScanModeSelected(scanMode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ScanMode.entries.size),
                enabled = enabled,
                modifier = Modifier
                    .testTag(scanMode.testTag)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = scanMode.label)
            }
        }
    }
}

private val ScanMode.label: String
    get() = when (this) {
        ScanMode.DOCUMENT -> "Document"
        ScanMode.MIXED -> "Mixed"
        ScanMode.COLOR -> "Color"
    }

private val ScanMode.testTag: String
    get() = when (this) {
        ScanMode.DOCUMENT -> DoclyTestTags.SCAN_MODE_DOCUMENT_OPTION
        ScanMode.MIXED -> DoclyTestTags.SCAN_MODE_MIXED_OPTION
        ScanMode.COLOR -> DoclyTestTags.SCAN_MODE_COLOR_OPTION
    }
