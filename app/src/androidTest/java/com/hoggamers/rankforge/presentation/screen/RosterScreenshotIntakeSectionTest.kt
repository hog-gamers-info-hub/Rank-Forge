package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

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
                    )
                },
            ),
        )
        composeTestRule.setContent {
            RankForgeTheme {
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

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_SET_STATUS_TEST_TAG)
            .assertTextContains(context.getString(R.string.roster_screenshot_intake_complete, 3, 3))
        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_INTAKE_REMOVE_BUTTON_TEST_TAG_PREFIX + 3)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(3, removedIndex)
            assertEquals(2, state.selectedImageCount)
            assertTrue(state.isIncompleteDraftSet)
        }
    }
}
