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
import com.hoggamers.rankforge.domain.tournament.KillValidationError
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class MatchKillScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun killScreenRendersTwelveRowsAndSaveBackActions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchKillScreen(
                    uiState = availableState(),
                    onKillsChanged = { _, _ -> },
                    onSave = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_KILL_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(1)
        composeTestRule
            .onNodeWithTag(MATCH_KILL_ROW_TEST_TAG_PREFIX + "12")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_KILL_BACK_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun invalidKillErrorIsVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchKillScreen(
                    uiState = availableState(
                        validationErrors = mapOf(1 to KillValidationError.INVALID),
                    ),
                    onKillsChanged = { _, _ -> },
                    onSave = {},
                    onBackPressed = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Enter a whole number of 0 or more.").assertIsDisplayed()
    }

    @Test
    fun saveActionInvokesCallback() {
        var saveCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchKillScreen(
                    uiState = availableState(),
                    onKillsChanged = { _, _ -> },
                    onSave = { saveCount++ },
                    onBackPressed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, saveCount) }
    }

    private fun availableState(
        validationErrors: Map<Int, KillValidationError> = emptyMap(),
    ) = MatchKillUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchNumber = 1,
        rows = (1..12).map { slotNumber ->
            MatchKillRowUiState(
                teamSlotNumber = slotNumber,
                teamName = "Team $slotNumber",
                killsInput = "",
            )
        },
        validationErrors = validationErrors,
    )
}
