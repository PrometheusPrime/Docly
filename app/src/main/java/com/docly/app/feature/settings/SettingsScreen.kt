package com.docly.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.docly.app.domain.model.AppSettings
import com.docly.app.domain.model.PdfExportQuality
import com.docly.app.domain.model.ReaderThemeMode
import com.docly.app.domain.model.ScanMode
import com.docly.app.domain.model.StorageUsage
import com.docly.app.domain.model.ThemeMode
import com.docly.app.ui.components.DoclyErrorContent
import com.docly.app.ui.components.DoclyLoadingContent
import com.docly.app.ui.components.DoclyScreenScaffold
import com.docly.app.ui.components.doclyMinimumTouchTarget
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DoclyScreenScaffold(
        title = "Settings",
        screenTestTag = DoclyTestTags.SETTINGS_SCREEN,
        modifier = modifier,
        onNavigateBack = onNavigateBack
    ) {
        if (uiState.isLoading) {
            DoclyLoadingContent(message = "Loading settings...")
        } else {
            uiState.errorMessage?.let { message ->
                DoclyErrorContent(title = "Settings issue", message = message)
            }
            AppearanceSection(settings = uiState.settings, onEvent = onEvent)
            ScannerSection(settings = uiState.settings, onEvent = onEvent)
            ReaderSection(settings = uiState.settings, onEvent = onEvent)
            ExportSection(settings = uiState.settings, onEvent = onEvent)
            StorageSection(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun AppearanceSection(settings: AppSettings, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection(title = "Appearance", testTag = DoclyTestTags.SETTINGS_APPEARANCE_SECTION) {
        ChoiceButton(
            label = "Use system theme",
            selected = settings.themeMode == ThemeMode.SYSTEM,
            testTag = DoclyTestTags.SETTINGS_THEME_SYSTEM_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnThemeModeSelected(ThemeMode.SYSTEM)) }
        )
        ChoiceButton(
            label = "Light theme",
            selected = settings.themeMode == ThemeMode.LIGHT,
            testTag = DoclyTestTags.SETTINGS_THEME_LIGHT_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnThemeModeSelected(ThemeMode.LIGHT)) }
        )
        ChoiceButton(
            label = "Dark theme",
            selected = settings.themeMode == ThemeMode.DARK,
            testTag = DoclyTestTags.SETTINGS_THEME_DARK_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnThemeModeSelected(ThemeMode.DARK)) }
        )
        SwitchSettingRow(
            title = "Dynamic color",
            description = "Use Android system colors when available.",
            checked = settings.dynamicColorEnabled,
            testTag = DoclyTestTags.SETTINGS_DYNAMIC_COLOR_TOGGLE,
            onCheckedChange = { enabled -> onEvent(SettingsUiEvent.OnDynamicColorChanged(enabled)) }
        )
    }
}

@Composable
private fun ScannerSection(settings: AppSettings, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection(title = "Scanner", testTag = DoclyTestTags.SETTINGS_SCANNER_SECTION) {
        ChoiceButton(
            label = "Document mode",
            selected = settings.defaultScanMode == ScanMode.DOCUMENT,
            testTag = DoclyTestTags.SETTINGS_SCAN_MODE_DOCUMENT_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnDefaultScanModeSelected(ScanMode.DOCUMENT)) }
        )
        ChoiceButton(
            label = "Mixed mode",
            selected = settings.defaultScanMode == ScanMode.MIXED,
            testTag = DoclyTestTags.SETTINGS_SCAN_MODE_MIXED_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnDefaultScanModeSelected(ScanMode.MIXED)) }
        )
        ChoiceButton(
            label = "Color mode",
            selected = settings.defaultScanMode == ScanMode.COLOR,
            testTag = DoclyTestTags.SETTINGS_SCAN_MODE_COLOR_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnDefaultScanModeSelected(ScanMode.COLOR)) }
        )
        SwitchSettingRow(
            title = "Auto capture",
            description = "Capture when the preview is steady and readable.",
            checked = settings.autoCaptureEnabled,
            testTag = DoclyTestTags.SETTINGS_AUTO_CAPTURE_TOGGLE,
            onCheckedChange = { enabled -> onEvent(SettingsUiEvent.OnAutoCaptureChanged(enabled)) }
        )
    }
}

@Composable
private fun ReaderSection(settings: AppSettings, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection(title = "Reader", testTag = DoclyTestTags.SETTINGS_READER_SECTION) {
        Text(
            text = "Text size ${settings.readerTextSizeSp.toInt()}sp",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onEvent(SettingsUiEvent.OnReaderTextSizeChanged(settings.readerTextSizeSp - TEXT_SIZE_STEP))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.SETTINGS_READER_TEXT_SIZE_DECREASE_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "A-")
            }
            OutlinedButton(
                onClick = {
                    onEvent(SettingsUiEvent.OnReaderTextSizeChanged(settings.readerTextSizeSp + TEXT_SIZE_STEP))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(DoclyTestTags.SETTINGS_READER_TEXT_SIZE_INCREASE_ACTION)
                    .doclyMinimumTouchTarget()
            ) {
                Text(text = "A+")
            }
        }
        ChoiceButton(
            label = "Light reader",
            selected = settings.readerThemeMode == ReaderThemeMode.LIGHT,
            testTag = DoclyTestTags.SETTINGS_READER_THEME_LIGHT_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnReaderThemeSelected(ReaderThemeMode.LIGHT)) }
        )
        ChoiceButton(
            label = "Dark reader",
            selected = settings.readerThemeMode == ReaderThemeMode.DARK,
            testTag = DoclyTestTags.SETTINGS_READER_THEME_DARK_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnReaderThemeSelected(ReaderThemeMode.DARK)) }
        )
    }
}

@Composable
private fun ExportSection(settings: AppSettings, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection(title = "Export", testTag = DoclyTestTags.SETTINGS_EXPORT_SECTION) {
        ChoiceButton(
            label = "High PDF quality",
            selected = settings.defaultPdfQuality == PdfExportQuality.HIGH,
            testTag = DoclyTestTags.SETTINGS_PDF_QUALITY_HIGH_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnDefaultPdfQualitySelected(PdfExportQuality.HIGH)) }
        )
        ChoiceButton(
            label = "Medium PDF quality",
            selected = settings.defaultPdfQuality == PdfExportQuality.MEDIUM,
            testTag = DoclyTestTags.SETTINGS_PDF_QUALITY_MEDIUM_ACTION,
            onClick = { onEvent(SettingsUiEvent.OnDefaultPdfQualitySelected(PdfExportQuality.MEDIUM)) }
        )
    }
}

@Composable
private fun StorageSection(uiState: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection(title = "Storage", testTag = DoclyTestTags.SETTINGS_STORAGE_SECTION) {
        if (uiState.isStorageLoading) {
            DoclyLoadingContent(message = "Calculating storage...")
        } else {
            StorageLine(label = "Documents", bytes = uiState.storageUsage.documentsBytes)
            StorageLine(label = "Thumbnails", bytes = uiState.storageUsage.thumbnailsBytes)
            StorageLine(label = "Exports", bytes = uiState.storageUsage.exportsBytes)
            StorageLine(label = "Temporary files", bytes = uiState.storageUsage.tempBytes)
            StorageLine(label = "Cache", bytes = uiState.storageUsage.cacheBytes)
            StorageLine(label = "Total shown", bytes = uiState.storageUsage.totalBytes)
        }
        OutlinedButton(
            onClick = { onEvent(SettingsUiEvent.OnRefreshStorageClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.SETTINGS_REFRESH_STORAGE_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = "Refresh storage")
        }
        Button(
            onClick = { onEvent(SettingsUiEvent.OnClearCacheClicked) },
            enabled = !uiState.isClearingCache,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DoclyTestTags.SETTINGS_CLEAR_CACHE_ACTION)
                .doclyMinimumTouchTarget()
        ) {
            Text(text = if (uiState.isClearingCache) "Clearing..." else "Clear cache")
        }
    }
}

@Composable
private fun SettingsSection(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = title },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        content()
        HorizontalDivider(color = DividerDefaults.color)
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, testTag: String, onClick: () -> Unit) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .testTag(testTag)
        .doclyMinimumTouchTarget()
        .semantics { contentDescription = if (selected) "$label selected" else label }
    if (selected) {
        Button(onClick = onClick, modifier = buttonModifier) {
            Text(text = label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier) {
            Text(text = label)
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag(testTag)
                .semantics { contentDescription = title }
        )
    }
}

@Composable
private fun StorageLine(label: String, bytes: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = bytes.toReadableSize(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun Long.toReadableSize(): String = when {
    this <= 0L -> "0 B"
    this < 1024L -> "$this B"
    this < 1024L * 1024L -> "${this / 1024L} KB"
    else -> "${this / (1024L * 1024L)} MB"
}

private const val TEXT_SIZE_STEP = 2f

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    DoclyTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                isLoading = false,
                isStorageLoading = false,
                storageUsage = StorageUsage(
                    documentsBytes = 4_500_000L,
                    thumbnailsBytes = 230_000L,
                    tempBytes = 30_000L,
                    exportsBytes = 120_000L,
                    cacheBytes = 512_000L
                )
            ),
            onEvent = {},
            onNavigateBack = {}
        )
    }
}
