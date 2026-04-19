package com.docly.app.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.docly.app.app.MainActivity
import com.docly.app.ui.util.DoclyTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Phase05NavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun appStartsOnScannerScreen() {
        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_SCREEN)
            .assertIsDisplayed()
    }

    @Test
    fun scannerShowsPhotoImportActions() {
        composeRule.onNodeWithTag(DoclyTestTags.IMPORT_SINGLE_PHOTO_ACTION)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.IMPORT_MULTIPLE_PHOTOS_ACTION)
            .assertIsDisplayed()
    }

    @Test
    fun scannerCanNavigateToLibraryAndBackToScanner() {
        composeRule.onNodeWithTag(DoclyTestTags.OPEN_LIBRARY_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCANNER_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_SCREEN)
            .assertIsDisplayed()
    }

    @Test
    fun placeholderWorkflowNavigatesThroughAllPhase05Screens() {
        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_REVIEW_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.REVIEW_EDITOR_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.EDITOR_METADATA_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.METADATA_EXPORT_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.EXPORT_LIBRARY_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCREEN)
            .assertIsDisplayed()
    }
}
