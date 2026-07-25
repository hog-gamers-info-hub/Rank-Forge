package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.domain.tournament.MatchField
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchValidationError
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class MatchCreationScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun matchCreationFormRendersExpectedFieldsAndActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchCreationScreen(
                    uiState = MatchCreationUiState(),
                    onMatchNumberChanged = {},
                    onMatchDateChanged = {},
                    onMapNameChanged = {},
                    onSubmit = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_NUMBER_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_DATE_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_MAP_FIELD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_BACK_ACTION_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun validationErrorsAreVisibleAndCreateActionCanBeInvoked() {
        var submitCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchCreationScreen(
                    uiState = MatchCreationUiState(
                        validationErrors = mapOf(
                            MatchField.MATCH_NUMBER to MatchValidationError.INVALID,
                        ),
                    ),
                    onMatchNumberChanged = {},
                    onMatchDateChanged = {},
                    onMapNameChanged = {},
                    onSubmit = { submitCount++ },
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Enter a positive whole number.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, submitCount) }
    }

    @Test
    fun detailsShowsCreatedDraftAndBlocksAtTenMatches() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentDetailsScreen(
                    uiState = TournamentDetailsUiState(
                        isLoading = false,
                        tournament = TournamentDetailsItemUiState(
                            id = "stable-id",
                            name = "Summer Cup",
                            date = LocalDate.of(2026, 7, 24),
                            organizerName = "Alex",
                            organizerContactNumber = "123",
                            status = TournamentStatus.CONFIRMED,
                            slots = emptyList(),
                            matches = (1..10).map { number ->
                                MatchUiState(
                                    id = "match-$number",
                                    matchNumber = number,
                                    date = LocalDate.of(2026, 7, 24),
                                    mapName = "Bermuda",
                                    status = MatchStatus.DRAFT,
                                )
                            },
                        ),
                    ),
                    onBackToList = {},
                    onEnterTeams = {},
                    onCreateMatch = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Maximum of 10 matches reached.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Match 1").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Status: DRAFT").assertCountEquals(10)
    }
}
