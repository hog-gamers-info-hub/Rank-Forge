package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary
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
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.Available(
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
                    onLoadCloudTournaments = {},
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
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.AuthenticationRequired,
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertIsDisplayed()
    }

    @Test
    fun queuedStateShowsRestorationSavedLocallyMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.Queued,
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertTextEquals("Restore could not complete. Saved locally for later sync.")
    }

    @Test
    fun queuePersistenceFailureStateShowsRestorationLocalSaveFailureMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.QueuePersistenceFailure,
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertTextEquals("Restore failed and could not be saved locally.")
    }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
