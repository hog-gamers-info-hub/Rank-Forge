package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class TournamentListAndDetailsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyListShowsCreateTournamentAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.tournament_list_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_EMPTY_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun nonEmptyListShowsApprovedTournamentFieldsOnly() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    onCreateTournament = {},
                    onOpenTournamentDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organizer: Alex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: DRAFT").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Contact: 123").assertCountEquals(0)
    }

    @Test
    fun tappingListItemInvokesDetailsCallback() {
        var openedTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentListScreen(
                    uiState = TournamentListUiState(
                        tournaments = listOf(tournamentListItem()),
                    ),
                    onCreateTournament = {},
                    onOpenTournamentDetails = { openedTournamentId = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "stable-id").performClick()

        composeTestRule.runOnIdle { assertEquals("stable-id", openedTournamentId) }
    }

    @Test
    fun detailsScreenShowsAllApprovedFields() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = tournamentDetailsItem(),
                    ),
                    onBackToList = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Organizer: Alex").assertIsDisplayed()
        composeTestRule.onNodeWithText("Contact: 123").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: DRAFT").assertIsDisplayed()
    }

    @Test
    fun detailsNotFoundStateRendersSafeMessageAndAction() {
        var backCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(isLoading = false),
                    onBackToList = { backCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_not_found_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.back_to_tournament_list_action)).performClick()
        composeTestRule.runOnIdle { assertEquals(1, backCount) }
    }

    private fun tournamentListItem() = TournamentListItemUiState(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        status = TournamentStatus.DRAFT,
    )

    private fun tournamentDetailsItem() = TournamentDetailsItemUiState(
        id = "stable-id",
        name = "Summer Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
    )
}
