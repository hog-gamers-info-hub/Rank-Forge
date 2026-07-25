package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class RosterReviewScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun allTwelveTeamsAndPlayersRenderInOrder() {
        composeTestRule.setContent {
            RankForgeTheme {
                RosterReviewScreen(
                    uiState = validState(),
                    onEditTeam = {},
                    onEditRoster = {},
                    onConfirm = {},
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(ROSTER_REVIEW_SLOT_ITEM_TEST_TAG_PREFIX)
            .assertCountEquals(0)
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(ROSTER_REVIEW_SLOT_ITEM_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule
                .onNodeWithText("Team $slotNumber")
                .performScrollTo()
                .assertIsDisplayed()
            (0..3).forEach { playerIndex ->
                composeTestRule
                    .onNodeWithTag(
                        ROSTER_REVIEW_PLAYER_TEST_TAG_PREFIX + "${slotNumber}_$playerIndex",
                    )
                    .assertTextEquals("Player ${playerIndex + 1}: Slot $slotNumber Player $playerIndex")
            }
        }
    }

    @Test
    fun invalidRosterDisablesConfirmationAndShowsIssue() {
        composeTestRule.setContent {
            RankForgeTheme {
                RosterReviewScreen(
                    uiState = validState().copy(
                        validationIssues = listOf(
                            RosterValidationIssueUiState.MissingTeamName(2),
                        ),
                    ),
                    onEditTeam = {},
                    onEditRoster = {},
                    onConfirm = {},
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(R.string.validation_missing_team_name, 2)).assertIsDisplayed()
    }

    @Test
    fun confirmationStateIsVisibleAndEditActionsTargetTheirSlot() {
        var state by mutableStateOf(validState())
        var editedTeamSlot = 0
        var editedRosterSlot = 0
        var confirmationCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                RosterReviewScreen(
                    uiState = state,
                    onEditTeam = { editedTeamSlot = it },
                    onEditRoster = { editedRosterSlot = it },
                    onConfirm = {
                        confirmationCount += 1
                        state = state.copy(status = TournamentStatus.CONFIRMED)
                    },
                    onBackToTeamEntry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ROSTER_REVIEW_SLOT_ITEM_TEST_TAG_PREFIX + 7)
            .performScrollTo()
        composeTestRule.onNodeWithText(context.getString(R.string.edit_team_action, 7)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.edit_roster_action, 7)).performClick()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(7, editedTeamSlot)
            assertEquals(7, editedRosterSlot)
            assertEquals(1, confirmationCount)
        }
        composeTestRule
            .onNodeWithTag(ROSTER_REVIEW_STATUS_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.roster_review_confirmed_status)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG).assertIsNotEnabled()
    }

    private fun validState() = RosterReviewUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "stable-id",
        status = TournamentStatus.DRAFT,
        teams = (1..12).map { slotNumber ->
            RosterReviewTeamUiState(
                slotNumber = slotNumber,
                teamName = "Team $slotNumber",
                players = (0..3).map { playerIndex ->
                    RosterReviewPlayerUiState(
                    playerIndex = playerIndex,
                    displayName = "Slot $slotNumber Player $playerIndex",
                )
                },
            )
        },
    )
}
