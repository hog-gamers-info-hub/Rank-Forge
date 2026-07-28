package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.tournament.MatchResultValidationError
import com.hoggamers.rankforge.domain.tournament.MatchCorrectionRecord
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchReviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reviewScreenShowsAllRowsRestoredValuesAndValidStatus() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").assertCountEquals(1)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_VALID_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Players: Player One").assertIsDisplayed()
        composeTestRule.onNodeWithText("Placement: 7").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kills: 3").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "12")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun reviewScreenShowsOverallAndRowValidationIssues() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(
                        validationErrors = mapOf(
                            1 to setOf(MatchResultValidationError.MISSING_PLACEMENT),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_ISSUES_STATUS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Issue: Placement is missing.").assertIsDisplayed()
    }

    @Test
    fun reviewActionsInvokePlacementKillAndDetailsCallbacks() {
        var placementCount = 0
        var killCount = 0
        var detailsCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = { placementCount++ },
                    onEnterKills = { killCount++ },
                    onBackToDetails = { detailsCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, placementCount)
            assertEquals(1, killCount)
            assertEquals(1, detailsCount)
        }
    }

    @Test
    fun photoPickerActionIsAvailableAndSelectedStateIsVisible() {
        var photoPickerActionCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        selectedScreenshotUri = "content://picker/selected",
                        isSelectedScreenshotValidated = true,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onSelectScreenshot = { photoPickerActionCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_PHOTO_PICKER_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SELECTED_SCREENSHOT_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(1, photoPickerActionCount) }
    }

    @Test
    fun photoPickerActionIsDisabledWhileARequestIsActive() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(isPhotoPickerRequestActive = true),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_PHOTO_PICKER_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun invalidImageSelectionShowsValidationErrorWithoutValidatedConfirmation() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        selectedScreenshotUri = "content://picker/unsupported",
                        imageValidationError = ImageValidationError.UNSUPPORTED_FORMAT,
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_PHOTO_PICKER_ERROR_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SELECTED_SCREENSHOT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun finalizeActionRequiresConfirmation() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState(),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onFinalize = { finalizeCount++ },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This will make the match read-only. You can still review its results.")
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, finalizeCount) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, finalizeCount) }
    }

    @Test
    fun finalizedReviewIsReadOnlyAndShowsFinalizedState() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: FINALIZED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Finalized matches are read-only.").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun correctionActionRequiresConfirmation() {
        var correctionCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(status = MatchStatus.FINALIZED),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                    onStartCorrection = { correctionCount++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithText("This opens an editable correction copy. The finalized result stays unchanged until you submit it.")
            .assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, correctionCount) }
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, correctionCount) }
    }

    @Test
    fun correctionHistoryShowsPreviousAndCorrectedValues() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchReviewScreen(
                    uiState = availableState().copy(
                        status = MatchStatus.FINALIZED,
                        correctionHistory = listOf(
                            MatchCorrectionRecord(
                                previousPlacements = listOf(MatchPlacement(1, 7)),
                                previousKills = listOf(MatchKill(1, 3)),
                                correctedPlacements = listOf(MatchPlacement(1, 2)),
                                correctedKills = listOf(MatchKill(1, 8)),
                            ),
                        ),
                    ),
                    onEnterPlacements = {},
                    onEnterKills = {},
                    onBackToDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Previous finalized result — Slot 1: placement 7, kills 3")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrected result — Slot 1: placement 2, kills 8")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun availableState(
        validationErrors: Map<Int, Set<MatchResultValidationError>> = emptyMap(),
    ) = MatchReviewUiState(
        isLoading = false,
        isAvailable = true,
        tournamentId = "tournament-id",
        matchId = "match-id",
        matchNumber = 1,
        rows = (1..12).map { slotNumber ->
            MatchReviewRowUiState(
                teamSlotNumber = slotNumber,
                teamName = "Team $slotNumber",
                playerNames = if (slotNumber == 1) listOf("Player One") else emptyList(),
                placementInput = when {
                    slotNumber == 1 -> "7"
                    slotNumber <= 7 -> (slotNumber - 1).toString()
                    else -> slotNumber.toString()
                },
                killsInput = if (slotNumber == 1) "3" else "0",
                validationErrors = validationErrors[slotNumber].orEmpty(),
            )
        },
        validationErrors = validationErrors,
    )
}
