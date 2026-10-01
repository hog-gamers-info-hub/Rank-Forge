package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
                                stageName = "Organizer",
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
    fun loadingStateShowsFetchingMessageAndIndeterminateProgress() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.Loading,
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_LOADING_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Fetching your cloud tournaments...").assertIsDisplayed()
    }

    @Test
    fun restoringStateKeepsListVisibleAndDisablesEveryRestoreAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.Restoring(
                        tournamentId = TOURNAMENT_ID,
                        tournamentName = "Summer Cup",
                    ),
                    availableTournaments = summaries(),
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Restoring Summer Cup...").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(
                TOURNAMENT_CLOUD_RESTORATION_ITEM_PROGRESS_TEST_TAG_PREFIX + TOURNAMENT_ID,
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + TOURNAMENT_ID)
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + SECOND_TOURNAMENT_ID)
            .assertIsNotEnabled()
    }

    @Test
    fun completedRestoreKeepsListAvailableAndAllowsAnotherRestore() {
        var restoredTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.Success("Summer Cup"),
                    availableTournaments = summaries(),
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = { restoredTournamentId = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertTextEquals("Tournament restored to local storage: Summer Cup")
        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + SECOND_TOURNAMENT_ID)
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(SECOND_TOURNAMENT_ID, restoredTournamentId) }
    }

    @Test
    fun failedRestoreKeepsAvailableListAccessible() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentCloudRestorationSection(
                    uiState = TournamentCloudRestorationUiState.NetworkFailure,
                    availableTournaments = summaries(),
                    onLoadCloudTournaments = {},
                    onRestoreCloudTournament = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_STATUS_TEST_TAG + "_message")
            .assertTextEquals("Restore failed because the network is unavailable.")
        composeTestRule
            .onNodeWithTag(TOURNAMENT_CLOUD_RESTORATION_ITEM_TEST_TAG_PREFIX + TOURNAMENT_ID)
            .assertIsEnabled()
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
        const val SECOND_TOURNAMENT_ID = "22222222-2222-2222-2222-222222222222"

        fun summaries() = listOf(
            TournamentCloudRestorationSummary(
                id = TOURNAMENT_ID,
                name = "Summer Cup",
                stageName = "Organizer",
                status = "draft",
            ),
            TournamentCloudRestorationSummary(
                id = SECOND_TOURNAMENT_ID,
                name = "Winter Cup",
                stageName = "Organizer",
                status = "draft",
            ),
        )
    }
}
