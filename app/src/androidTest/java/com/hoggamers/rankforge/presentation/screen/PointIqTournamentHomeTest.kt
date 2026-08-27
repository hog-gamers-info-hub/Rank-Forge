package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import java.time.ZoneId
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
    fun tournamentSummaryCardShowsNameGameModeCountsAndLastUpdated() {
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
                    onClick = {},
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
        composeTestRule.onNodeWithText(
            context.getString(R.string.pointiq_home_last_updated, "— 27 Aug 2026"),
        ).assertIsDisplayed()
    }
}
