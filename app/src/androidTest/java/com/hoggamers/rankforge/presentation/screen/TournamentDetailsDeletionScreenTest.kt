package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentDetailsDeletionScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deleteTournamentActionOpensConfirmationWithNameAndCancelKeepsDetails() {
        var deleteCalls = 0
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    onDeleteTournament = { deleteCalls++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_DIALOG_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete Tournament?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Are you sure you want to delete Summer Cup? This will permanently delete the tournament, all matches, and all screenshots.",
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").assertIsDisplayed()

        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_CANCEL_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, deleteCalls) }
    }

    @Test
    fun confirmingDeleteInvokesCallbackOnceAndDeletingStateDisablesAction() {
        var deleteCalls = 0
        var deleting by mutableStateOf(false)
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    onDeleteTournament = {
                        deleteCalls++
                        deleting = true
                    },
                    isDeleting = deleting,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, deleteCalls)
        }
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_PROGRESS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_ACTION_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun deletingStateShowsProgressAndPreventsSecondInvocation() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    isDeleting = true,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_PROGRESS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_ACTION_TEST_TAG).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun failureKeepsDetailsVisibleAndShowsSafeMessage() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = detailsState(),
                    onBackToList = {},
                    onEnterTeams = {},
                    deletionError = TournamentDeletionUiError.REMOTE_FAILURE,
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_DELETE_ERROR_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_delete_remote_error)).assertIsDisplayed()
    }

    private fun detailsState() = TournamentDetailsUiState(
        isLoading = false,
        tournament = TournamentDetailsItemUiState(
            id = "stable-id",
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "000",
            status = TournamentStatus.CONFIRMED,
            slots = emptyList(),
        ),
    )
}
