package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
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
    fun emptySlotsShowCompactSelectorsAndSelectionCallbacks() {
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
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        (1..3).forEach { index ->
            composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + index)
                .assertIsDisplayed()
        }
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(3)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        (1..3).forEach { index ->
            composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + index)
                .assertIsNotSelected()
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .performClick()
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .performClick()
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 3)
            .performClick()
            .assertIsNotSelected()

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
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.match_lobby_screenshot_intake_title))
            .assertDoesNotExist()
    }

    @Test
    fun selectedSlotsShowDetailsAndActionsUseExactSlotIndex() {
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
                                    selectedScreenshotUri = "file:///private/lobby-2.png",
                                )
                                else -> slot
                            }
                        },
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = { cropIndexes += it },
                    onRemove = { removeIndexes += it },
                    compactActions = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .performClick()
            .assertIsSelected()
        composeTestRule.onAllNodesWithText("Selected").assertCountEquals(2)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .performClick()
            .assertIsSelected()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .performClick()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 3)
            .performClick()
            .assertIsNotSelected()

        composeTestRule.runOnIdle {
            assertEquals(listOf(1, 2, 3), selectedIndexes)
            assertEquals(listOf(1), cropIndexes)
            assertEquals(listOf(1), removeIndexes)
        }
    }

    @Test
    fun saveLobbyTemplateIsEnabledOnlyForThreeConfirmedDraftSlots() {
        var saveCount = 0
        val completeSlots = defaultMatchLobbyScreenshotSlots().map { slot ->
            slot.copy(
                hasLinkedAsset = true,
                selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                selectedScreenshotWidth = 1920,
                selectedScreenshotHeight = 1080,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "lobby",
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.DRAFT,
                        slots = completeSlots,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    onSaveLobbyForNextMatches = { saveCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, saveCount) }
    }

    @Test
    fun removingLastSelectedSlotClearsPagerAndActions() {
        val selectedSlot = defaultMatchLobbyScreenshotSlots().first().copy(
            hasLinkedAsset = true,
            selectedScreenshotUri = "file:///private/lobby-1.png",
        )
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(
                    MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = listOf(selectedSlot) + defaultMatchLobbyScreenshotSlots().drop(1),
                    ),
                )
            }
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = state,
                    onSelect = {},
                    onCrop = {},
                    onRemove = { index ->
                        state = state.copy(
                            slots = state.slots.map { slot ->
                                if (slot.index == index) {
                                    slot.copy(hasLinkedAsset = false, selectedScreenshotUri = null)
                                } else {
                                    slot
                                }
                            },
                        )
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .performClick()
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertIsNotSelected()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
    }

    @Test
    fun selectedSlotsSwipeThroughOnlySelectedSlotsAndUpdateActiveHighlight() {
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

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .assertIsNotSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 3)
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 3)
            .assertIsDisplayed()
    }

    @Test
    fun missingLocalFilePreservesRecoveryAndFinalizedAndBusyProtection() {
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
                                    isLocalFileMissing = true,
                                    selectedScreenshotUri = "file:///private/missing.png",
                                )
                                2 -> slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/busy.png",
                                    isPreservationInProgress = true,
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

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(R.string.match_lobby_screenshot_missing_local_file),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
    }

    @Test
    fun finalizedSelectedSlotRemainsViewableButActionsAreDisabled() {
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

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
    }
}
