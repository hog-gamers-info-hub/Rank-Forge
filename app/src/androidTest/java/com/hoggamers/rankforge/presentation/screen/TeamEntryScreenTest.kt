package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class TeamEntryScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun teamEntryRendersTwelveInputsWithAccessibleSlotLabels() {
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = teamEntrySlots(),
                    ),
                    onTeamNameChanged = { _, _ -> },
                    onSave = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithText(context.getString(R.string.team_name_slot_label, slotNumber))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun editingAndSavingPersistsAllEditedNamesThroughCallbacks() {
        var slots by mutableStateOf(teamEntrySlots())
        var savedNamesBySlotNumber = emptyMap<Int, String>()
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = slots,
                    ),
                    onTeamNameChanged = { slotNumber, teamName ->
                        slots = slots.map { slot ->
                            if (slot.slotNumber == slotNumber) {
                                slot.copy(teamName = teamName)
                            } else {
                                slot
                            }
                        }
                    },
                    onSave = {
                        savedNamesBySlotNumber = slots.associate { it.slotNumber to it.teamName }
                    },
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + 1)
            .performTextInput("Alpha")
        composeTestRule
            .onNodeWithTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + 2)
            .performScrollTo()
            .performTextInput("Bravo")
        composeTestRule
            .onNodeWithText(context.getString(R.string.save_team_names_action))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals("Alpha", savedNamesBySlotNumber[1])
            assertEquals("Bravo", savedNamesBySlotNumber[2])
            assertEquals("", savedNamesBySlotNumber[3])
        }
    }

    @Test
    fun teamEntryDoesNotRenderPlayerValidationOrRosterConfirmationControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = teamEntrySlots(),
                    ),
                    onTeamNameChanged = { _, _ -> },
                    onSave = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Player").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Player count").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Validate roster").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Confirm roster").assertCountEquals(0)
    }

    @Test
    fun validationIssuesRemainInStateButAreNotRendered() {
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = teamEntrySlots(),
                        validationIssues = listOf(
                            RosterValidationIssueUiState.DuplicateTeamName(
                                slotNumber = 2,
                                firstSlotNumber = 1,
                                normalizedName = "Alpha",
                            ),
                        ),
                    ),
                    onTeamNameChanged = { _, _ -> },
                    onSave = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.roster_validation_issues_title))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(
                context.getString(
                    R.string.validation_duplicate_team_name,
                    2,
                    1,
                    "Alpha",
                ),
            )
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(TEAM_ENTRY_SLOT_INPUT_TEST_TAG_PREFIX + 1)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.save_team_names_action))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun saveErrorRemainsVisibleWhenValidationIssuesAreHidden() {
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = teamEntrySlots(),
                        validationIssues = listOf(
                            RosterValidationIssueUiState.InvalidPlayerCount(
                                slotNumber = 1,
                                playerCount = 0,
                            ),
                        ),
                        hasSaveError = true,
                    ),
                    onTeamNameChanged = { _, _ -> },
                    onSave = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.team_names_save_error))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.roster_validation_issues_title))
            .assertCountEquals(0)
    }

    @Test
    fun gapMessageIsRendered() {
        composeTestRule.setContent {
            RankForgeTheme {
                TeamEntryScreen(
                    uiState = TeamEntryUiState(
                        isLoading = false,
                        slots = teamEntrySlots(),
                        hasTeamNameGap = true,
                    ),
                    onTeamNameChanged = { _, _ -> },
                    onSave = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.team_entry_gap_message)).assertIsDisplayed()
    }

    private fun teamEntrySlots() = (1..12).map { slotNumber ->
        TeamEntrySlotUiState(
            slotNumber = slotNumber,
            teamName = "",
        )
    }
}
