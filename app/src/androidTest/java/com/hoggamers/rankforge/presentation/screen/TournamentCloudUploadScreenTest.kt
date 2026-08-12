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
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class TournamentCloudUploadScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idleStateShowsManualUploadAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    showLegacyControls = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun manualUploadActionInvokesSelectedTournamentCallback() {
        var uploadedTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    showLegacyControls = true,
                    onUpload = { uploadedTournamentId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_UPLOAD_ACTION_TEST_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(TOURNAMENT_ID, uploadedTournamentId)
        }
    }

    @Test
    fun queuedUploadStateShowsSavedForLaterSyncMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    showLegacyControls = true,
                    uploadUiState = TournamentCloudUploadUiState.Queued,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG)
            .assertTextEquals("Upload could not complete. Saved locally for later sync.")
    }

    @Test
    fun queuePersistenceFailureStateShowsLocalSaveFailureMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    showLegacyControls = true,
                    uploadUiState = TournamentCloudUploadUiState.QueuePersistenceFailure,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_CLOUD_UPLOAD_STATUS_TEST_TAG)
            .assertTextEquals("Upload failed and could not be saved locally.")
    }

    private fun detailsState() = TournamentDetailsUiState(
        isLoading = false,
        tournament = TournamentDetailsItemUiState(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Alex",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
            slots = TeamSlot.SLOT_NUMBERS.map { TeamSlotUiState(it, "") },
        ),
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
