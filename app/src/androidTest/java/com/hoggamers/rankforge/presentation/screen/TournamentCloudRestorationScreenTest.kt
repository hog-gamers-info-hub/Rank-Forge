package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentCloudRestorationScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun availableCloudTournamentCanBeSelectedForManualRestore() {
        var restoredTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = true),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                    restorationUiState = TournamentCloudRestorationUiState.Available(
                        listOf(
                            TournamentCloudRestorationSummary(
                                id = TOURNAMENT_ID,
                                name = "Summer Cup",
                                date = "2026-07-24",
                                organizerName = "Organizer",
                                status = "draft",
                            ),
                        ),
                    ),
                    onRestoreCloudTournament = { restoredTournamentId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + TOURNAMENT_ID)
            .performClick()
        composeTestRule.runOnIdle { assertEquals(TOURNAMENT_ID, restoredTournamentId) }
    }

    @Test
    fun authenticationRequiredStateIsVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    authUiState = AuthUiState(isSignedIn = false),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                    onOpenAuth = {},
                    restorationUiState = TournamentCloudRestorationUiState.AuthenticationRequired,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertIsDisplayed()
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
