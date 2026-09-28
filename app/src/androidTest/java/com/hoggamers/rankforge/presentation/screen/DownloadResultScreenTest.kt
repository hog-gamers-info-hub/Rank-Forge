package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.data.export.FreeDesignTemplateRegistry
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.io.ByteArrayOutputStream
import java.time.LocalDate
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

    @Test
    fun pointTableSettingsIsAvailableForImageAndFreeDesignButNotMyDesign() {
        val savedDetails = PointTableDetailsUiState(
            organizationName = "Saved Org",
            date = LocalDate.of(2026, 9, 28),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.IMAGE,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    pointTableDetails = savedDetails,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Free design settings")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Point Table Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved Org").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 Sep 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Free Design").performClick()
        composeTestRule.onNodeWithContentDescription("Free design settings")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Point Table Details").assertIsDisplayed()
        composeTestRule.onNodeWithText("Saved Org").assertIsDisplayed()
        composeTestRule.onNodeWithText("28 Sep 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("My Design").performClick()
        composeTestRule.onAllNodesWithContentDescription("Free design settings")
            .assertCountEquals(0)
    }

    @Test
    fun cancellingPointTableDetailsDiscardsDraftEdits() {
        var applyCalls = 0
        val appliedDetails = PointTableDetailsUiState(organizationName = "Saved Org")
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.FREE_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    pointTableDetails = appliedDetails,
                    onPointTableDetailsApply = { applyCalls++ },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Free design settings").performClick()
        composeTestRule.onNodeWithText("Saved Org").performTextInput(" changed")
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithContentDescription("Free design settings").performClick()

        composeTestRule.onNodeWithText("Saved Org").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, applyCalls) }
    }

    @Test
    fun applyingPointTableDetailsTrimsOrganisationName() {
        var appliedDetails: PointTableDetailsUiState? = null
        composeTestRule.setContent {
            RankForgeTheme {
                DownloadResultScreen(
                    matches = listOf(DownloadResultMatchOption("match-1", 1)),
                    onBack = {},
                    initialDesign = DownloadResultDesignType.FREE_DESIGN,
                    previewState = DownloadResultPreviewState.ResultImage(testPngBytes()),
                    onPointTableDetailsApply = { appliedDetails = it },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Free design settings").performClick()
        composeTestRule.onNodeWithContentDescription("Organisation Name")
            .performTextInput("  HOG Gamers  ")
        composeTestRule.onNodeWithText("Apply").performClick()

        composeTestRule.runOnIdle {
            assertEquals("HOG Gamers", appliedDetails?.organizationName)
            assertEquals(null, appliedDetails?.date)
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
