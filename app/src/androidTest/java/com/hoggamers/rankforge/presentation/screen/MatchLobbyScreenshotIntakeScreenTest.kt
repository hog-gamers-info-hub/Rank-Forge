package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
        val selectedIndexes = mutableListOf<Int>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots(),
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Screenshot 1 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX + 1)
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_PAGE_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 2 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX + 1)
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX + 2)
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 3 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_INDICATOR_TEST_TAG_PREFIX + 3)
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_PAGE_TEST_TAG)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 3)
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 2 of 3").assertIsDisplayed()

        composeTestRule.runOnIdle { assertEquals(listOf(1, 2, 3), selectedIndexes) }
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
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Screenshot 1 of 3").assertIsDisplayed()
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
        val selectedIndexes = mutableListOf<Int>()
        val cropIndexes = mutableListOf<Int>()
        val removeIndexes = mutableListOf<Int>()
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
                    onSelect = { selectedIndexes += it },
                    onCrop = { cropIndexes += it },
                    onRemove = { removeIndexes += it },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Replace").assertIsDisplayed()
        composeTestRule.onNodeWithText("Crop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 3)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 2 of 3").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.match_lobby_screenshot_missing_local_file),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Screenshot 3 of 3").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 3)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 3)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 3)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 3)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(1, 2, 3), selectedIndexes)
            assertEquals(listOf(1, 3), cropIndexes)
            assertEquals(listOf(1, 3), removeIndexes)
        }
    }

    @Test
    fun staticActionRowFollowsActivePageAndBusyState() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            slot.copy(
                                hasLinkedAsset = true,
                                selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                                selectedScreenshotWidth = 1920,
                                selectedScreenshotHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.0, 0.0, 1.0, 1.0),
                                cropProfileId = "lobby",
                                isPreservationInProgress = slot.index == 2,
                            )
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
    }

    @Test
    fun lobbyPreviewContainerKeepsStableHeightAcrossPages() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            slot.copy(
                                hasLinkedAsset = true,
                                selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                                selectedScreenshotWidth = 1920,
                                selectedScreenshotHeight = 1080,
                                confirmedCrop = when (slot.index) {
                                    1 -> OcrNormalizedCropRect(0.0, 0.0, 1.0, 0.5)
                                    2 -> OcrNormalizedCropRect(0.0, 0.0, 0.5, 1.0)
                                    else -> OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
                                },
                                cropProfileId = "lobby",
                            )
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        val pager = composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
        val initialHeight = pager.fetchSemanticsNode().size.height
        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(initialHeight, pager.fetchSemanticsNode().size.height)
        pager.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(initialHeight, pager.fetchSemanticsNode().size.height)
    }
}
