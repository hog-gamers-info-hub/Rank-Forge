package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.mutableStateOf
import com.hoggamers.rankforge.domain.tournament.MatchResultRowInput
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchCorrectionScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun correctionScreenShowsPreviousValuesAndEditableCorrectionFields() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchCorrectionScreen(
                    uiState = availableState(),
                    onPlacementChanged = { _, _ -> },
                    onKillsChanged = { _, _ -> },
                    onSubmit = {},
                    onDiscard = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Previous finalized result — Slot 1: placement 7, kills 3").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_PLACEMENT_FIELD_TEST_TAG_PREFIX + "1").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX + "12")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun submitCorrectionRequiresConfirmation() {
        var submitCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchCorrectionScreen(
                    uiState = availableState(),
                    onPlacementChanged = { _, _ -> },
                    onKillsChanged = { _, _ -> },
                    onSubmit = { submitCount++ },
                    onDiscard = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("This replaces the current finalized result and preserves the previous result in correction history.")
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, submitCount) }
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, submitCount) }
    }

    @Test
    fun correctionFieldsShowValidationBlockSubmissionAndAllowValidConfirmation() {
        val state = mutableStateOf(validAvailableState())
        var submitCount = 0

        fun updateRow(teamSlotNumber: Int, placement: String? = null, kills: String? = null) {
            val updatedRows = state.value.rows.map { row ->
                if (row.teamSlotNumber == teamSlotNumber) {
                    row.copy(
                        placementInput = placement ?: row.placementInput,
                        killsInput = kills ?: row.killsInput,
                    )
                } else {
                    row
                }
            }
            val validation = ValidateMatchResultUseCase()(
                updatedRows.map { row ->
                    MatchResultRowInput(
                        teamSlotNumber = row.teamSlotNumber,
                        placement = row.placementInput,
                        kills = row.killsInput,
                    )
                },
            )
            state.value = state.value.copy(
                rows = updatedRows.map { row ->
                    row.copy(validationErrors = validation.errorsByTeamSlot[row.teamSlotNumber].orEmpty())
                },
                validationErrors = validation.errorsByTeamSlot,
            )
        }

        composeTestRule.setContent {
            RankForgeTheme {
                MatchCorrectionScreen(
                    uiState = state.value,
                    onPlacementChanged = { slot, value -> updateRow(slot, placement = value) },
                    onKillsChanged = { slot, value -> updateRow(slot, kills = value) },
                    onSubmit = { submitCount++ },
                    onDiscard = {},
                    onBackPressed = {},
                )
            }
        }

        val placementField = composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_PLACEMENT_FIELD_TEST_TAG_PREFIX + "1")
            .performScrollTo()
        placementField.performTextClearance()
        placementField.performTextInput("13")

        composeTestRule.onNodeWithText("Placement is invalid.").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        composeTestRule.onAllNodesWithText("Submit correction?").assertCountEquals(0)
        composeTestRule.runOnIdle { assertEquals(0, submitCount) }

        placementField.performTextClearance()
        placementField.performTextInput("1")
        composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithText("This replaces the current finalized result and preserves the previous result in correction history.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, submitCount) }
    }

    @Test
    fun draftStateDoesNotRenderCorrectionControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchCorrectionScreen(
                    uiState = MatchCorrectionUiState(isLoading = false, isAvailable = false),
                    onPlacementChanged = { _, _ -> },
                    onKillsChanged = { _, _ -> },
                    onSubmit = {},
                    onDiscard = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Only an existing finalized match can enter correction mode.").assertIsDisplayed()
    }

    private fun availableState() = MatchCorrectionUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchNumber = 1,
        rows = (1..12).map { slot ->
            MatchCorrectionRowUiState(
                teamSlotNumber = slot,
                teamName = "Team $slot",
                previousPlacement = if (slot == 1) "7" else slot.toString(),
                previousKills = if (slot == 1) "3" else "0",
                placementInput = if (slot == 1) "2" else slot.toString(),
                killsInput = if (slot == 1) "8" else "0",
                validationErrors = emptySet(),
            )
        },
        validationErrors = emptyMap(),
    )

    private fun validAvailableState(): MatchCorrectionUiState {
        val base = availableState()
        return base.copy(
            rows = base.rows.map { row ->
                row.copy(placementInput = row.teamSlotNumber.toString())
            },
        )
    }
}
