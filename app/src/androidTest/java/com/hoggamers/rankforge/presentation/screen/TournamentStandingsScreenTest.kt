package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentStandingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun finalizedStandingsDisplayAllDerivedFields() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(standingRow(order = 1, slot = 2)),
                    ),
                    onBackToTournamentDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + "2").assertIsDisplayed()
        listOf(
            context.getString(R.string.tournament_standing_order_value, 1),
            context.getString(R.string.tournament_standing_team_slot_value, 2),
            context.getString(R.string.tournament_standing_total_points_value, 19),
            context.getString(R.string.tournament_standing_position_points_value, 9),
            context.getString(R.string.tournament_standing_kill_points_value, 10),
            context.getString(R.string.tournament_standing_first_place_finishes_value, 0),
            context.getString(R.string.tournament_standing_latest_placement_value, 2),
            context.getString(R.string.tournament_standing_matches_included_value, 1),
        ).forEach { text -> composeTestRule.onNodeWithText(text).assertIsDisplayed() }
    }

    @Test
    fun completeTieIsShownAsUnresolved() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(
                            standingRow(order = 1, slot = 1, isCompleteTie = true),
                            standingRow(order = 2, slot = 2, isCompleteTie = true),
                        ),
                    ),
                    onBackToTournamentDetails = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + "2")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.tournament_standing_complete_tie_message))
            .assertCountEquals(2)
    }

    @Test
    fun noFinalizedMatchesShowEmptyState() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(isLoading = false),
                    onBackToTournamentDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_EMPTY_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_standings_empty_title))
            .assertIsDisplayed()
    }

    private fun standingRow(
        order: Int,
        slot: Int,
        isCompleteTie: Boolean = false,
    ) = TournamentStandingRowUiState(
        displayOrder = order,
        teamSlotNumber = slot,
        teamName = null,
        totalPoints = 19,
        totalPositionPoints = 9,
        totalKillPoints = 10,
        firstPlaceFinishes = 0,
        latestMatchPlacement = 2,
        matchesIncluded = 1,
        isCompleteTie = isCompleteTie,
    )
}
