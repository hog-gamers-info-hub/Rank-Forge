package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class DraftMatchCloudSyncScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleStateShowsManualDraftMatchSyncAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun manualDraftMatchSyncActionInvokesSelectedTournamentCallback() {
        var syncedTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    onSyncDraftMatches = { syncedTournamentId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DRAFT_MATCH_CLOUD_SYNC_ACTION_TEST_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(TOURNAMENT_ID, syncedTournamentId)
        }
    }

    @Test
    fun queuedStateShowsDraftSyncSavedLocallyMessage() {
        composeTestRule.setContent { RankForgeTheme { TournamentDetailsScreen(detailsState(), {}, {}, draftMatchSyncUiState = DraftMatchCloudSyncUiState.Queued) } }
        composeTestRule.onNodeWithTag(DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG)
            .assertTextEquals("Draft-match sync could not complete. Saved locally for later sync.")
    }

    @Test
    fun queuePersistenceFailureStateShowsDraftSyncLocalSaveFailureMessage() {
        composeTestRule.setContent { RankForgeTheme { TournamentDetailsScreen(detailsState(), {}, {}, draftMatchSyncUiState = DraftMatchCloudSyncUiState.QueuePersistenceFailure) } }
        composeTestRule.onNodeWithTag(DRAFT_MATCH_CLOUD_SYNC_STATUS_TEST_TAG)
            .assertTextEquals("Draft-match sync failed and could not be saved locally.")
    }

    private fun detailsState() = TournamentDetailsUiState(
        isLoading = false,
        tournament = TournamentDetailsItemUiState(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Alex",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
            slots = TeamSlot.SLOT_NUMBERS.map { TeamSlotUiState(it, "") },
        ),
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
