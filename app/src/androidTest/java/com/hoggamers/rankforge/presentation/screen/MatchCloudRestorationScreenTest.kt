package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchCloudRestorationScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()
    @Test fun restoreActionShowsAndInvokesSelectedTournament() {
        var restored: String? = null
        composeTestRule.setContent { RankForgeTheme { TournamentDetailsScreen(details(), {}, {}, onRestoreMatches = { restored = it }) } }
        composeTestRule.onNodeWithTag(MATCH_CLOUD_RESTORE_ACTION_TEST_TAG).performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag(MATCH_CLOUD_RESTORE_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(TOURNAMENT_ID, restored) }
    }
    @Test fun authenticationRequiredStateIsVisible() {
        composeTestRule.setContent { RankForgeTheme { TournamentDetailsScreen(details(), {}, {}, matchCloudRestorationUiState = MatchCloudRestorationUiState.AuthenticationRequired) } }
        composeTestRule.onNodeWithTag(MATCH_CLOUD_RESTORE_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
    }
    private fun details() = TournamentDetailsUiState(false, tournament = TournamentDetailsItemUiState(TOURNAMENT_ID, "Cup", LocalDate.of(2026, 7, 24), "Alex", "123", TournamentStatus.CONFIRMED, TeamSlot.SLOT_NUMBERS.map { TeamSlotUiState(it, "") }))
    private companion object { const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111" }
}
