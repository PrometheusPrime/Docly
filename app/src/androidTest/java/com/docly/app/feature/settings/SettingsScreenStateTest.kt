package com.docly.app.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.docly.app.domain.model.StorageUsage
import com.docly.app.ui.theme.DoclyTheme
import com.docly.app.ui.util.DoclyTestTags
import org.junit.Rule
import org.junit.Test

class SettingsScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSectionsAndStorageActionsRender() {
        composeRule.setContent {
            DoclyTheme {
                SettingsScreen(
                    uiState = state(),
                    onEvent = {},
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_APPEARANCE_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_SCANNER_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_READER_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_EXPORT_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_STORAGE_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_CLEAR_CACHE_ACTION).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsSettingsActionsReachableOnCompactWidth() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 2f)) {
                DoclyTheme {
                    SettingsScreen(
                        uiState = state(),
                        onEvent = {},
                        onNavigateBack = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_THEME_SYSTEM_ACTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.SETTINGS_CLEAR_CACHE_ACTION).performScrollTo().assertIsDisplayed()
    }

    private fun state(): SettingsUiState = SettingsUiState(
        isLoading = false,
        isStorageLoading = false,
        storageUsage = StorageUsage(documentsBytes = 100L, thumbnailsBytes = 10L, cacheBytes = 5L)
    )
}
