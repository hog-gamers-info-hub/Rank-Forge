package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.R
import android.graphics.Bitmap
import com.hoggamers.rankforge.data.ocr.matchlobby.AndroidMatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerDualOcrResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrEngine
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrEngineEvidence
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchLobbyScreenshotIntakeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun globalTeamCropPagerSortsByDetectedSlotWithoutVisibleLabels() {
        val shuffledSlotNumbers = listOf(9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8)
        val previewsByScreenshot = shuffledSlotNumbers
            .chunked(RosterVisibleSlotPosition.entries.size)
            .mapIndexed { screenshotOffset, screenshotSlots ->
                (screenshotOffset + 1) to MatchLobbyTeamCropPreviewResult.Available(
                    RosterVisibleSlotPosition.entries.mapIndexed { index, position ->
                        val detectedSlot = screenshotSlots[index]
                        val height = when (detectedSlot % 3) {
                            0 -> 320
                            1 -> 300
                            else -> 360
                        }
                        MatchLobbyTeamCropPreview(
                            position,
                            detectedSlot,
                            AndroidMatchLobbyTeamCropPreviewImage(
                                Bitmap.createBitmap(1000, height, Bitmap.Config.ARGB_8888),
                            ),
                        )
                    },
                )
            }
            .toMap()
        val selectedSlots = defaultMatchLobbyScreenshotSlots().map { slot ->
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
                CompositionLocalProvider(LocalMatchLobbyTeamCropPreviews provides previewsByScreenshot) {
                    MatchLobbyScreenshotIntakeScreen(
                        uiState = MatchLobbyScreenshotIntakeUiState(
                            isLoading = false,
                            isAvailable = true,
                            slots = selectedSlots,
                        ),
                        onSelect = {},
                        onCrop = {},
                        onRemove = {},
                        compactActions = true,
                    )
                }
            }
        }

        val pager = composeTestRule.onNodeWithTag(MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX + "global")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_1")
            .assertIsDisplayed()
        val pagerBounds = pager.getUnclippedBoundsInRoot()
        val firstCardBounds = composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_1",
        ).getUnclippedBoundsInRoot()
        val pagerWidth = (pagerBounds.right - pagerBounds.left).value
        val firstCardWidth = (firstCardBounds.right - firstCardBounds.left).value
        val firstCardHeight = (firstCardBounds.bottom - firstCardBounds.top).value
        assertEquals(pagerWidth, firstCardWidth, 1f)
        val firstImageBounds = composeTestRule.onNodeWithContentDescription("Slot 1")
            .getUnclippedBoundsInRoot()
        val firstImageWidth = (firstImageBounds.right - firstImageBounds.left).value
        val firstImageHeight = (firstImageBounds.bottom - firstImageBounds.top).value
        assertEquals(300f / 1000f * firstImageWidth, firstImageHeight, 1f)
        assertEquals(
            firstCardBounds.top.value + (firstCardHeight - firstImageHeight) / 2f,
            firstImageBounds.top.value,
            1f,
        )
        val fixedPagerHeight = firstCardHeight
        composeTestRule.onNodeWithContentDescription("Slot 1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Slot 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("1 / 4").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("1 / 12").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(
            MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_2",
        ).assertCountEquals(0)

        (2..12).forEach { expectedSlot ->
            composeTestRule.onNodeWithTag(MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX + "global")
                .performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(
                MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_" + expectedSlot,
            ).assertIsDisplayed()
            assertEquals(
                fixedPagerHeight,
                composeTestRule.onNodeWithTag(
                    MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_" + expectedSlot,
                ).getUnclippedBoundsInRoot().let { bounds ->
                    (bounds.bottom - bounds.top).value
                },
                1f,
            )
            composeTestRule.onNodeWithContentDescription("Slot $expectedSlot").assertIsDisplayed()
        }
    }

    @Test
    fun globalTeamCropPagerDoesNotSynthesizeMissingDetectedSlots() {
        val selected = defaultMatchLobbyScreenshotSlots().first().copy(
            hasLinkedAsset = true,
            selectedScreenshotUri = "file:///private/lobby-1.png",
            selectedScreenshotWidth = 1920,
            selectedScreenshotHeight = 1080,
            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            cropProfileId = "lobby",
        )
        val previewImage = AndroidMatchLobbyTeamCropPreviewImage(
            Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888),
        )
        val detectedSlots = listOf(1, 2, 4, 5)
        val previews = MatchLobbyTeamCropPreviewResult.Available(
            RosterVisibleSlotPosition.entries.mapIndexed { index, position ->
                MatchLobbyTeamCropPreview(position, detectedSlots[index], previewImage)
            },
        )

        composeTestRule.setContent {
            RankForgeTheme {
                CompositionLocalProvider(LocalMatchLobbyTeamCropPreviews provides mapOf(1 to previews)) {
                    MatchLobbyScreenshotIntakeScreen(
                        uiState = MatchLobbyScreenshotIntakeUiState(
                            isLoading = false,
                            isAvailable = true,
                            slots = listOf(selected) + defaultMatchLobbyScreenshotSlots().drop(1),
                        ),
                        onSelect = {},
                        onCrop = {},
                        onRemove = {},
                        compactActions = true,
                    )
                }
            }
        }

        val pagerTag = MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX + "global"
        composeTestRule.onNodeWithContentDescription("Slot 1").assertIsDisplayed()
        detectedSlots.drop(1).forEach { expectedSlot ->
            composeTestRule.onNodeWithTag(pagerTag).performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithContentDescription("Slot $expectedSlot").assertIsDisplayed()
        }
        composeTestRule.onAllNodesWithTag(
            MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_3",
        ).assertCountEquals(0)
    }

    @Test
    fun currentTeamPageShowsOnlyItsOwnFourGeneratedRowsInOrder() {
        val selectedSlots = defaultMatchLobbyScreenshotSlots().map { slot ->
            slot.copy(
                hasLinkedAsset = true,
                selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                selectedScreenshotWidth = 1920,
                selectedScreenshotHeight = 1080,
                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                cropProfileId = "lobby",
            )
        }
        val previews = RosterVisibleSlotPosition.entries.mapIndexed { index, visibleSlotPosition ->
            val slotNumber = index + 1
            MatchLobbyTeamCropPreview(
                visibleSlotPosition = visibleSlotPosition,
                detectedSlotNumber = slotNumber,
                image = AndroidMatchLobbyTeamCropPreviewImage(
                    Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888),
                ),
                playerRowPreviews = generatedRows(slotNumber),
            )
        }

        composeTestRule.setContent {
            RankForgeTheme {
                CompositionLocalProvider(
                    LocalMatchLobbyTeamCropPreviews provides mapOf(
                        1 to MatchLobbyTeamCropPreviewResult.Available(previews),
                    ),
                    LocalMatchLobbyTeamNames provides mapOf(1 to "APX"),
                ) {
                    MatchLobbyScreenshotIntakeScreen(
                        uiState = MatchLobbyScreenshotIntakeUiState(
                            isLoading = false,
                            isAvailable = true,
                            slots = selectedSlots,
                        ),
                        onSelect = {},
                        onCrop = {},
                        onRemove = {},
                        compactActions = true,
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_TEAM_SLOT_LABEL_TEST_TAG_PREFIX + "1",
        ).assertIsDisplayed()
        val cropBounds = composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_CARD_TEST_TAG_PREFIX + "slot_1",
        ).getUnclippedBoundsInRoot()
        val dataBounds = composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_DATA_TEST_TAG_PREFIX + "slot_1",
        ).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(dataBounds.top > cropBounds.bottom)
        composeTestRule.onNodeWithText("Slot - 1 | Team Name - APX").assertExists()
        LobbyPlayerRow.entries.forEach { row ->
            composeTestRule.onNodeWithTag(
                MATCH_LOBBY_TEAM_CROP_PLAYER_NAME_TEST_TAG_PREFIX +
                    "slot_1_row_${row.ordinal + 1}",
            ).assertExists()
        }
        composeTestRule.onNodeWithText("1. PP Row 1").assertExists()
        composeTestRule.onNodeWithText("2. PP Row 2").assertExists()
        composeTestRule.onAllNodesWithText("Resolved: Resolved Row 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("ML: ML Row 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("PP: PP Row 1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Status: SIMILAR_PP_SELECTED").assertCountEquals(0)
        composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_PREVIEWS_TEST_TAG_PREFIX + "global",
        ).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(
            MATCH_LOBBY_TEAM_CROP_PLAYER_NAME_TEST_TAG_PREFIX + "slot_2_row_1",
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(
            MATCH_LOBBY_TEAM_CROP_PLAYER_NAME_TEST_TAG_PREFIX + "slot_1_row_1",
        ).assertCountEquals(0)
    }

    @Test
    fun emptySlotsShowOneSequentialCompactSelectorAndSelectionCallback() {
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
                    compactSelectors = true,
                    compactActions = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                1,
            ),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        (1..3).forEach { index ->
            composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + index)
                .assertCountEquals(0)
        }
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + "1")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .performClick()

        composeTestRule.runOnIdle { assertEquals(listOf(1), selectedIndexes) }
    }

    @Test
    fun embeddedModeSuppressesOnlyStandaloneTitleAndUsesSequentialSelector() {
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
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                1,
            ),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Empty").assertCountEquals(0)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.match_lobby_screenshot_intake_title))
            .assertDoesNotExist()
    }

    @Test
    fun sequentialSelectorTargetsSlotTwoAfterSlotOneIsSelected() {
        val selectedIndexes = mutableListOf<Int>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) {
                                slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/lobby-1.png",
                                )
                            } else {
                                slot
                            }
                        },
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                2,
            ),
        ).assertIsDisplayed()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(2), selectedIndexes) }
    }

    @Test
    fun sequentialSelectorTargetsSlotThreeAfterSlotsOneAndTwoAreSelected() {
        val selectedIndexes = mutableListOf<Int>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index in 1..2) {
                                slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                                )
                            } else {
                                slot
                            }
                        },
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                3,
            ),
        ).performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(3), selectedIndexes) }
    }

    @Test
    fun allSelectedSlotsHideSequentialSelector() {
        val completeSlots = defaultMatchLobbyScreenshotSlots().map { slot ->
            slot.copy(
                hasLinkedAsset = true,
                selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = completeSlots,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun nonSequentialSelectedSlotsTargetTheLowestMissingSlot() {
        val selectedIndexes = mutableListOf<Int>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1 || slot.index == 3) {
                                slot.copy(
                                    hasLinkedAsset = true,
                                    selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
                                )
                            } else {
                                slot
                            }
                        },
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                2,
            ),
        ).performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(2), selectedIndexes) }
    }

    @Test
    fun missingLocalLinkedSlotIsNotUsedAsSequentialTarget() {
        val selectedIndexes = mutableListOf<Int>()
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) {
                                slot.copy(
                                    hasLinkedAsset = true,
                                    isLocalFileMissing = true,
                                    selectedScreenshotUri = "file:///private/missing.png",
                                )
                            } else {
                                slot
                            }
                        },
                    ),
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                    compactActions = true,
                )
            }
        }

        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                2,
            ),
        ).performClick()
        composeTestRule.runOnIdle { assertEquals(listOf(2), selectedIndexes) }
    }

    @Test
    fun busySequentialTargetRemainsDisplayedAndDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) {
                                slot.copy(isPreservationInProgress = true)
                            } else {
                                slot
                            }
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                1,
            ),
        ).assertIsDisplayed()
    }

    @Test
    fun finalizedMatchKeepsSequentialSelectionDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        slots = defaultMatchLobbyScreenshotSlots(),
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_NEXT_SELECT_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
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
    fun embeddedSaveLobbyTemplateUsesOneCompactTopRowAction() {
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
                    compactSelectors = true,
                    compactActions = true,
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertCountEquals(1)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertIsOff()
            .performClick()
        composeTestRule.onAllNodesWithText("Save Lobby").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Unsave Lobby").assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(1, saveCount) }
    }

    @Test
    fun offIncompleteLobbyShowsDisabledSaveLobby() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.DRAFT,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOff()
            .assertIsNotEnabled()
    }

    @Test
    fun onLobbyShowsCheckedSaveLobbySwitchAndInvokesUnsaveCallback() {
        var unsaveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        isLobbySavedForNextMatches = true,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    onUnsaveLobbyForNextMatches = { unsaveCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Unsave Lobby").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOn()
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, unsaveCount) }
    }

    @Test
    fun saveLobbyLabelAndSwitchStateFollowObservedStateAcrossRecomposition() {
        var saveCount = 0
        var unsaveCount = 0
        composeTestRule.setContent {
            var saved by remember { mutableStateOf(false) }
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.DRAFT,
                        slots = completeLobbySlots(),
                        isLobbySavedForNextMatches = saved,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    onSaveLobbyForNextMatches = { saveCount++; saved = true },
                    onUnsaveLobbyForNextMatches = { unsaveCount++; saved = false },
                )
            }
        }

        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOff()
            .performClick()
        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Unsave Lobby").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOn()
            .performClick()
        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Unsave Lobby").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOff()
        composeTestRule.runOnIdle {
            assertEquals(1, saveCount)
            assertEquals(1, unsaveCount)
        }
    }

    @Test
    fun finalizedOffLobbyKeepsSaveDisabled() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        slots = completeLobbySlots(),
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsOff()
            .assertIsNotEnabled()
    }

    @Test
    fun lobbySwitchExposesStateDescriptionsAndDoesNotShowMutationSuccessText() {
        var uiState by mutableStateOf(
            MatchLobbyScreenshotIntakeUiState(
                isLoading = false,
                isAvailable = true,
                status = MatchStatus.DRAFT,
                slots = completeLobbySlots(),
                isLobbySavedForNextMatches = false,
                lobbyTemplateSaveStatus = MatchLobbyTemplateSaveStatus.UNSAVED,
            ),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = uiState,
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    onSaveLobbyForNextMatches = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Lobby not saved for next matches")
            .assertIsOff()
        composeTestRule.onAllNodesWithText("Lobby saved for next matches").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Lobby will not be reused for next matches.")
            .assertCountEquals(0)

        composeTestRule.runOnIdle {
            uiState = uiState.copy(
                status = MatchStatus.FINALIZED,
                isLobbySavedForNextMatches = true,
                lobbyTemplateSaveStatus = MatchLobbyTemplateSaveStatus.SAVED,
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Lobby saved for next matches")
            .assertIsOn()
        composeTestRule.onAllNodesWithText("Lobby saved for next matches").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Lobby will not be reused for next matches.")
            .assertCountEquals(0)
    }

    @Test
    fun lobbySwitchKeepsMutationFailureFeedback() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.DRAFT,
                        slots = completeLobbySlots(),
                        lobbyTemplateSaveStatus = MatchLobbyTemplateSaveStatus.FAILED,
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Could not update saved Lobby.").assertIsDisplayed()
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
    fun selectedSlotsSwipeThroughInCompactModeWithoutSelectorButtons() {
        val selectedIndexes = mutableListOf<Int>()
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
                    onSelect = { selectedIndexes += it },
                    onCrop = {},
                    onRemove = {},
                    compactSelectors = true,
                    compactActions = true,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(
            composeTestRule.activity.getString(
                R.string.match_lobby_screenshot_select_index_action,
                2,
            ),
        ).assertIsDisplayed()
            .performClick()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 3)
            .assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(listOf(2), selectedIndexes) }
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .assertIsDisplayed()
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

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
    fun finalizedSelectedSlotRemainsViewableButMutationActionsAreAbsent() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) slot.copy(
                                hasLinkedAsset = true,
                                selectedScreenshotUri = "file:///private/lobby-1.png",
                                selectedScreenshotWidth = 1920,
                                selectedScreenshotHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "lobby",
                            ) else slot
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
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 2)
            .assertIsNotEnabled()
    }

    @Test
    fun finalizedSelectedSlotInCompactModeKeepsPreviewButHidesMutationActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchLobbyScreenshotIntakeScreen(
                    uiState = MatchLobbyScreenshotIntakeUiState(
                        isLoading = false,
                        isAvailable = true,
                        status = MatchStatus.FINALIZED,
                        slots = defaultMatchLobbyScreenshotSlots().map { slot ->
                            if (slot.index == 1) slot.copy(
                                hasLinkedAsset = true,
                                selectedScreenshotUri = "file:///private/lobby-1.png",
                                selectedScreenshotWidth = 1920,
                                selectedScreenshotHeight = 1080,
                                confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                                cropProfileId = "lobby",
                            ) else slot
                        },
                    ),
                    onSelect = {},
                    onCrop = {},
                    onRemove = {},
                    showTitle = false,
                    compactSelectors = true,
                    compactActions = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
    }

    @Test
    fun processedLobbyOcrHidesOnlyScreenshotPreviewsAndActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                CompositionLocalProvider(LocalMatchLobbySourceSectionVisible provides false) {
                    MatchLobbyScreenshotIntakeScreen(
                        uiState = MatchLobbyScreenshotIntakeUiState(
                            isLoading = false,
                            isAvailable = true,
                            status = MatchStatus.DRAFT,
                            slots = completeLobbySlots(),
                        ),
                        onSelect = {},
                        onCrop = {},
                        onRemove = {},
                        showTitle = false,
                        compactSelectors = true,
                        compactActions = true,
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Lobby Details").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_DETAILS_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_DETAILS_STEP_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Save Lobby").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SAVE_TEMPLATE_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PREVIEW_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_PAGER_TEST_TAG)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SLOT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_SELECT_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_CROP_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_LOBBY_SCREENSHOT_INTAKE_REMOVE_TEST_TAG_PREFIX + 1)
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(
            composeTestRule.activity.getString(R.string.match_lobby_screenshot_replace_action),
        ).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(
            composeTestRule.activity.getString(R.string.match_review_screenshot_edit_action),
        ).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(
            composeTestRule.activity.getString(R.string.match_lobby_screenshot_remove_action),
        ).assertCountEquals(0)
    }

    private fun completeLobbySlots() = defaultMatchLobbyScreenshotSlots().map { slot ->
        slot.copy(
            hasLinkedAsset = true,
            selectedScreenshotUri = "file:///private/lobby-${slot.index}.png",
            selectedScreenshotWidth = 1920,
            selectedScreenshotHeight = 1080,
            confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
            cropProfileId = "lobby",
        )
    }

    private fun generatedRows(slotNumber: Int): List<LobbyPlayerRowCropPreview> =
        LobbyPlayerRow.entries.map { row ->
            val height = 90 + row.ordinal * 10
            val bounds = LobbyPlayerRowCropBounds(150, row.ordinal * 100, 1000, row.ordinal * 100 + height)
            LobbyPlayerRowCropPreview(
                row = row,
                boundsInTeamCrop = bounds,
                slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
                slotAnchorY = 150.0,
                structuralEvidence = "PP Row ${row.ordinal + 1}",
            )
        }
}
