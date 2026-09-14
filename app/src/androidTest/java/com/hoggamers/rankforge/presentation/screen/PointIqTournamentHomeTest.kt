package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PointIqTournamentHomeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tournamentSummaryCardShowsNameGameModeCountsWithoutUpdatedDate() {
        var clickedCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                PointIqTournamentSummaryCard(
                    tournament = TournamentListItemUiState(
                        id = "summary-id",
                        name = "Summer Cup",
                        date = LocalDate.of(2026, 8, 27),
                        organizerName = "Organizer",
                        status = TournamentStatus.DRAFT,
                        totalTeams = 2,
                        totalMatches = 3,
                        lastUpdatedEpochMillis = LocalDate.of(2026, 8, 27)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    ),
                    onClick = { clickedCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Free Fire MAX  •  Squad").assertIsDisplayed()
        val teamsText = context.getString(R.string.pointiq_home_team_count_many, 2)
        val matchesText = context.getString(R.string.pointiq_home_match_count_many, 3)
        composeTestRule.onNodeWithText(
            context.getString(R.string.pointiq_home_summary_line, teamsText, matchesText),
        ).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(
            context.getString(R.string.pointiq_home_last_updated, "27 Aug 2026"),
        ).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "summary-id")
            .performClick()
        assertEquals(1, clickedCount)
    }

    @Test
    fun emptyHomeShowsBottomCreateActionAndPlainEmptyMessage() {
        var createClickCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                PointIqTournamentHomeContent(
                    uiState = TournamentListUiState(),
                    onCreateTournament = { createClickCount++ },
                    onOpenTournamentDetails = {},
                    onOpenAllTournaments = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(POINTIQ_HOME_CREATE_TOURNAMENT_TEST_TAG)
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_list_empty_message))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Tournament Control").assertCountEquals(0)
        assertEquals(1, createClickCount)
    }

    @Test
    fun createActionFollowsRecentCardsInsideScrollableContent() {
        val tournaments = (1..12).map { number ->
            TournamentListItemUiState(
                id = "tournament-$number",
                name = "Tournament $number",
                date = LocalDate.of(2026, 8, number),
                organizerName = "Organizer",
                status = TournamentStatus.DRAFT,
                totalTeams = 12,
                totalMatches = number,
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                PointIqTournamentHomeContent(
                    uiState = TournamentListUiState(tournaments = tournaments),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAllTournaments = {},
                )
            }
        }

        val lastRecentCard =
            composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "tournament-12")
        val createAction = composeTestRule.onNodeWithTag(POINTIQ_HOME_CREATE_TOURNAMENT_TEST_TAG)

        lastRecentCard.performScrollTo().assertIsDisplayed()
        createAction.performScrollTo().assertIsDisplayed()
        assertTrue(
            createAction.fetchSemanticsNode().boundsInRoot.top >
                lastRecentCard.fetchSemanticsNode().boundsInRoot.top,
        )
    }
}
