package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RosterScreenshotIntakeSectionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun incompleteSetShowsAllThreeSelectionControlsAndKeepsManualRosterReviewOutsideItsState() {
        var selectedIndex = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RosterScreenshotIntakeSection(
                    uiState = RosterScreenshotIntakeUiState(tournamentId = "tournament-1"),
                    onSelectImage = { selectedIndex = it },
                    onRemoveImage = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SET_STATUS_TEST_TAG)
            .assertTextContains(context.getString(R.string.roster_screenshot_intake_incomplete, 0, 3))
        (1..3).forEach { index ->
            composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SELECT_BUTTON_TEST_TAG_PREFIX + index)
                .assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SELECT_BUTTON_TEST_TAG_PREFIX + 2)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(2, selectedIndex)
        }
    }

    @Test
    fun completeSetShowsReplacementAndRemovalControls() {
        var removedIndex = 0
        var state by mutableStateOf(
            RosterScreenshotIntakeUiState(
                tournamentId = "tournament-1",
                slots = (1..3).map { index ->
                    RosterScreenshotSlotUiState(
                        index = index,
                        selectedImageUri = "content://$index",
                        isSelectedImageValidated = true,
                        cropDraft = RosterScreenshotCropDefaults.FullImageCrop.toRosterScreenshotCropDraft(),
                        cropState = RosterScreenshotCropState.Set(RosterScreenshotCropDefaults.FullImageCrop),
                    )
                },
            ),
        )
        composeTestRule.setContent {
            RankForgeTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    RosterScreenshotIntakeSection(
                        uiState = state,
                        onSelectImage = {},
                        onRemoveImage = { index ->
                            removedIndex = index
                            state = state.copy(
                                slots = state.slots.map { slot ->
                                    if (slot.index == index) RosterScreenshotSlotUiState(index) else slot
                                },
                            )
                        },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SET_STATUS_TEST_TAG)
            .assertTextContains(context.getString(R.string.roster_screenshot_intake_complete, 3, 3))
        (1..3).forEach { index ->
            composeTestRule
                .onAllNodesWithTag(ROSTER_SCREENSHOT_INTAKE_SELECT_BUTTON_TEST_TAG_PREFIX + index)
                .assertCountEquals(1)
            composeTestRule
                .onAllNodesWithTag(ROSTER_SCREENSHOT_INTAKE_REMOVE_BUTTON_TEST_TAG_PREFIX + index)
                .assertCountEquals(1)
        }
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_REMOVE_BUTTON_TEST_TAG_PREFIX + 3)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(3, removedIndex)
            assertEquals(2, state.selectedImageCount)
            assertTrue(state.isIncompleteDraftSet)
        }
    }

    @Test
    fun selectedSlotShowsCropStatusAndRequestsDedicatedCropNavigation() {
        var openedCropIndex = 0
        var clearCropIndex = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RosterScreenshotIntakeSection(
                    uiState = RosterScreenshotIntakeUiState(
                        tournamentId = "tournament-1",
                        slots = listOf(
                            RosterScreenshotSlotUiState(
                                index = 1,
                                selectedImageUri = "content://one",
                                isSelectedImageValidated = true,
                            ),
                            RosterScreenshotSlotUiState(index = 2),
                            RosterScreenshotSlotUiState(index = 3),
                        ),
                    ),
                    onSelectImage = {},
                    onRemoveImage = {},
                    onOpenCropEditor = { openedCropIndex = it },
                    onClearCrop = { clearCropIndex = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_CROP_STATUS_TEST_TAG_PREFIX + 1)
            .assertTextContains(context.getString(R.string.roster_screenshot_crop_not_set))
        composeTestRule.onAllNodesWithTag(OCR_VISUAL_CROP_PREVIEW_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_OPEN_CROP_BUTTON_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_CLEAR_CROP_BUTTON_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, openedCropIndex)
            assertEquals(1, clearCropIndex)
        }
    }

    @Test
    fun cropReadySlotShowsEditCropAction() {
        var openedCropIndex = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RosterScreenshotIntakeSection(
                    uiState = RosterScreenshotIntakeUiState(
                        tournamentId = "tournament-1",
                        slots = listOf(
                            RosterScreenshotSlotUiState(
                                index = 1,
                                selectedImageUri = "content://one",
                                isSelectedImageValidated = true,
                                cropDraft = RosterScreenshotCropDefaults.FullImageCrop.toRosterScreenshotCropDraft(),
                                cropState = RosterScreenshotCropState.Set(
                                    RosterScreenshotCropDefaults.FullImageCrop,
                                ),
                            ),
                            RosterScreenshotSlotUiState(index = 2),
                            RosterScreenshotSlotUiState(index = 3),
                        ),
                    ),
                    onSelectImage = {},
                    onRemoveImage = {},
                    onOpenCropEditor = { openedCropIndex = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_CROP_STATUS_TEST_TAG_PREFIX + 1)
            .assertTextContains(context.getString(R.string.roster_screenshot_crop_ready))
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_OPEN_CROP_BUTTON_TEST_TAG_PREFIX + 1)
            .assertTextContains(context.getString(R.string.roster_screenshot_crop_edit_action))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, openedCropIndex)
        }
    }
}
