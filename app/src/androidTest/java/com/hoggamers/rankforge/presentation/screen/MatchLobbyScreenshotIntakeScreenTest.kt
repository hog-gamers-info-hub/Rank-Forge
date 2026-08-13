package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchLobbyScreenshotIntakeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptySlotsExposeSelectionCallbacks() {
        var selectedIndex = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots(),
                    ),
                    onSelect = { selectedIndex = it },
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.match_lobby_screenshot_intake_title))
            .assertIsDisplayed()

        composeTestRule.runOnIdle { assertEquals(2, selectedIndex) }
    }

    @Test
    fun embeddedModeSuppressesOnlyStandaloneTitleAndKeepsSlots() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots(),
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    showTitle = false,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.match_lobby_screenshot_intake_title))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
    }

    @Test
    fun finalizedSlotsDisableSelectionCropAndRemove() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) slot.copy(hasLinkedAsset = true) else slot
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
    }

    @Test
    fun linkedLocalSlotShowsPreviewAndMissingSlotDoesNot() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            when (slot.index) {
                                1 -> slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/lobby-1.png",
                                    selectedScreenshotWidth = 1920,
                                    selectedScreenshotHeight = 1080,
                                    confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                    cropProfileId = "lobby",
                                )
                                2 -> slot.copy(
                                    hasLinkedAsset = true,
                                    isLocalFileMissing = true,
                                    selectedScreenshotUri = "file:///private/lobby-2.png",
                                )
                                3 -> slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/lobby-3.png",
                                )
                                else -> slot
                            }
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 3)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
    }
}
