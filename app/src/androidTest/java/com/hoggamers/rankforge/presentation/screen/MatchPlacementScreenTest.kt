package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.domain.tournament.PlacementValidationError
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class MatchPlacementScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun placementScreenRendersTwelveRowsAndSaveBackActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState(),
                    onPlacementChanged = { _, _ -> },
                    onSave = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_ROW_TEST_TAG_PREFIX + "12")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_BACK_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun placementScreenUsesPersistedMatchNumberInTitle() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState().copy(matchNumber = 2),
                    onPlacementChanged = { _, _ -> },
                    onSave = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Match 2 placements").assertIsDisplayed()
    }

    @Test
    fun finalizedPlacementScreenIsReadOnly() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState().copy(
                        isReadOnly = true,
                        rows = availableState().rows.mapIndexed { index, row ->
                            if (index == 0) row.copy(placementInput = "1") else row
                        },
                    ),
                    onPlacementChanged = { _, _ -> },
                    onSave = {},
                    onResetDraft = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Slot 1 placement: 1").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_RESET_ACTION_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun invalidAndDuplicatePlacementErrorsAreVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState(
                        validationErrors = mapOf(
                            1 to PlacementValidationError.INVALID,
                            2 to PlacementValidationError.DUPLICATE,
                        ),
                    ),
                    onPlacementChanged = { _, _ -> },
                    onSave = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Enter a position from 1 to 12.").assertIsDisplayed()
        composeTestRule.onNodeWithText("That position is already assigned to another team.").assertIsDisplayed()
    }

    @Test
    fun saveActionInvokesCallback() {
        var saveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState(),
                    onPlacementChanged = { _, _ -> },
                    onSave = { saveCount++ },
                    onBackPressed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, saveCount) }
    }

    @Test
    fun resetActionInvokesCallback() {
        var resetCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchPlacementScreen(
                    uiState = availableState(),
                    onPlacementChanged = { _, _ -> },
                    onSave = {},
                    onResetDraft = { resetCount++ },
                    onBackPressed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_RESET_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This clears both placements and kills for this match.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_RESET_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, resetCount) }
    }

    private fun availableState(
        validationErrors: Map<Int, PlacementValidationError> = emptyMap(),
    ) = MatchPlacementUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchNumber = 1,
        rows = (1..12).map { slotNumber ->
            MatchPlacementRowUiState(
                teamSlotNumber = slotNumber,
                teamName = "Team $slotNumber",
                placementInput = "",
            )
        },
        validationErrors = validationErrors,
    )
}
