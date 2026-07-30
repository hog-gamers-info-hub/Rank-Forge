package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchOcrReviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingStateIsDisplayed() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = MatchOcrReviewUiState.Loading,
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun emptyStateIsDisplayed() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "synthetic-tournament",
                        matchId = "synthetic-match",
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.EMPTY).assertIsDisplayed()
        composeTestRule.onNodeWithText("No OCR evidence").assertIsDisplayed()
    }

    @Test
    fun errorStateIsDisplayed() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = MatchOcrReviewUiState.Error(
                        tournamentId = "synthetic-tournament",
                        matchId = "synthetic-match",
                        message = "Synthetic OCR review error.",
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.ERROR).assertIsDisplayed()
        composeTestRule.onNodeWithText("Synthetic OCR review error.").assertIsDisplayed()
    }

    @Test
    fun readyStateDisplaysAllTwelveRows() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.READY_CONTENT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.ROW_LIST).assertIsDisplayed()
        (0..11).forEach { rowIndex ->
            composeTestRule
                .onNodeWithTag(MatchOcrReviewTestTags.row(rowIndex))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun rowDisplaysPlacementKillsPlayerConfidenceSafetyAndSuggestions() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(0)).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.placement(0))
            .assertTextEquals("Placement: 1 (Accepted)")
        composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.kills(0))
            .assertTextEquals("Kills: 3 (Accepted)")
        composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.playerName(0))
            .assertTextEquals("Player evidence: Synthetic Unit 1 (Accepted)")
        composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.confidence(0))
            .assertTextEquals("Confidence: 96 (Automatic candidate)")
        composeTestRule
            .onNodeWithTag(MatchOcrReviewTestTags.safety(0))
            .assertTextEquals("Suggested slot: 1; safety: Safe automatic assignment")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.suggestions(0)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Rank 1: Slot 1, confidence 96, matches 4, coverage 100")
            .assertIsDisplayed()
    }

    @Test
    fun warningAndBlockerLabelsAreDisplayedAsText() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.warning(1)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Warning: Safety: Review required").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.blocking(2)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Blocker: Placement: Missing").assertIsDisplayed()
    }

    @Test
    fun readOnlyScreenDoesNotExposeEditSaveFinalizeOrAssignmentControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Edit placements").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Finalize match").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Assign team").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Correct finalized match").assertCountEquals(0)
    }

    @Test
    fun backActionInvokesCallback() {
        var backCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = { backCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.BACK_ACTION).performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, backCount)
        }
    }

    private fun readyState(): MatchOcrReviewUiState.Ready = MatchOcrReviewUiState.Ready(
        tournamentId = "synthetic-tournament",
        matchId = "synthetic-match",
        matchDisplayLabel = "Synthetic Match",
        rowCount = 12,
        rows = (0..11).map { rowIndex ->
            MatchOcrReviewRowUiState(
                rowIndex = rowIndex,
                expectedPlacementLabel = (rowIndex + 1).toString(),
                detectedPlacementDisplayValue = if (rowIndex == 2) "Unavailable" else (rowIndex + 1).toString(),
                placementStatusLabel = if (rowIndex == 2) "Missing" else "Accepted",
                detectedKillDisplayValue = if (rowIndex == 0) "3" else rowIndex.toString(),
                killStatusLabel = "Accepted",
                detectedPlayerNameEvidenceLabel = "Synthetic Unit ${rowIndex + 1}",
                playerNameStatusLabel = "Accepted",
                suggestedTeamSlotDisplayValue = (rowIndex + 1).toString(),
                confidenceScoreDisplayValue = if (rowIndex == 1) "82" else "96",
                confidenceTierLabel = if (rowIndex == 1) "Confirmation required" else "Automatic candidate",
                assignmentSafetyStatusLabel = when (rowIndex) {
                    1 -> "Review required"
                    2 -> "Manual required"
                    else -> "Safe automatic assignment"
                },
                topThreeSuggestionsSummary = listOf(
                    "Rank 1: Slot ${rowIndex + 1}, confidence 96, matches 4, coverage 100",
                ),
                warningLabels = if (rowIndex == 1) {
                    listOf("Safety: Review required")
                } else {
                    emptyList()
                },
                blockerLabels = if (rowIndex == 2) {
                    listOf("Placement: Missing")
                } else {
                    emptyList()
                },
                severity = when (rowIndex) {
                    1 -> MatchOcrReviewSeverity.WARNING
                    2 -> MatchOcrReviewSeverity.BLOCKING
                    else -> MatchOcrReviewSeverity.INFORMATIONAL
                },
            )
        },
        blockerCount = 1,
        warningCount = 1,
        safeRowCount = 10,
        manualRequiredRowCount = 1,
        reviewRequiredRowCount = 1,
        manualReviewRequired = true,
        hasUnavailableEvidence = false,
    )
}
