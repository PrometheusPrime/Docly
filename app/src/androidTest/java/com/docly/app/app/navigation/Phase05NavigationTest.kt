package com.docly.app.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
    fun appStartsOnHomeScreen() {
        composeRule.onNodeWithTag("home_screen")
            .assertIsDisplayed()
    }

    @Test
    fun homeCanNavigateToScanner() {
        composeRule.onNodeWithText("Scan")
            .performClick()

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DoclyTestTags.CAMERA_CAPTURE_ACTION)
            .assertExists()
    }

    @Test
    fun homeCanNavigateToDocumentsAndBackToScanner() {
        composeRule.onNodeWithText("Documents")
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCREEN)
            .assertIsDisplayed()

        composeRule.onNodeWithTag(DoclyTestTags.LIBRARY_SCANNER_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_SCREEN)
            .assertIsDisplayed()
    }

    @Test
    fun homeCanNavigateToCreate() {
        composeRule.onNodeWithText("Create")
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.CREATE_SCREEN)
            .assertIsDisplayed()
    }

    @Test
    fun createCanCreateTxtAndOpenEditor() {
        composeRule.onNodeWithText("Create")
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.CREATE_TITLE_FIELD)
            .performTextClearance()
        composeRule.onNodeWithTag(DoclyTestTags.CREATE_TITLE_FIELD)
            .performTextInput("Phase Five Notes")
        composeRule.onNodeWithTag(DoclyTestTags.CREATE_DOCUMENT_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_SCREEN)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(DoclyTestTags.DOCUMENT_EDITOR_CONTENT_FIELD)
            .assertExists()
    }

    @Test
    fun createPdfFromScanImagesNavigatesToScanner() {
        composeRule.onNodeWithText("Create")
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.CREATE_SCAN_PDF_ACTION)
            .performClick()

        composeRule.onNodeWithTag(DoclyTestTags.SCANNER_SCREEN)
            .assertIsDisplayed()
    }
}
