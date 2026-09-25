package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.export.FreeDesignTemplateRegistry
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadResultScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun generatedFreeDesignPreviewReplacesComingSoonAndEnablesDownload() {
        var downloadCalls = 0
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.FREE_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    onDownload = { _, design ->
                        assertEquals(DownloadResultDesignType.FREE_DESIGN, design)
                        downloadCalls++
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Free Design").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Coming soon...").assertCountEquals(0)
        composeTestRule.onNodeWithText("Download").assertIsEnabled().performClick()
        composeTestRule.runOnIdle { assertEquals(1, downloadCalls) }
    }

    @Test
    fun freeDesignTemplateOptionsAreSelectableAndRemainAfterDesignSwitch() {
        var selectedTemplateId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.FREE_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    onFreeDesignTemplateSelected = { selectedTemplateId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(
            DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX +
                FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX +
                FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        ).assertIsDisplayed().performClick()
        composeTestRule.runOnIdle {
            assertEquals(FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID, selectedTemplateId)
        }

        composeTestRule.onNodeWithText("Image").performClick()
        composeTestRule.onAllNodesWithTag(
            DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX +
                FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        ).assertCountEquals(0)

        composeTestRule.onNodeWithText("Free Design").performClick()
        composeTestRule.onNodeWithTag(
            DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX +
                FreeDesignTemplateRegistry.BLUE_TEMPLATE_ID,
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText("My Design").performClick()
        composeTestRule.onAllNodesWithTag(
            DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX +
                FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
        ).assertCountEquals(0)
    }

    @Test
    fun importYourDesignDelegatesTheCurrentDownloadSelection() {
        var importedSelection: DownloadResultSelection? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialResult = DownloadResultSelection.Match("match-1"),
                    previewState = DownloadResultPreviewState.ImportYourDesign,
                    onImportYourDesign = { importedSelection = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Import Your Design").performClick()
        composeTestRule.runOnIdle {
            assertEquals(DownloadResultSelection.Match("match-1"), importedSelection)
        }
    }

    private fun testPngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
