package com.hoggamers.rankforge.presentation.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
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
    fun readyStateSupportsGestureScrollingToLowerRowsAndBackAction() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(0)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(11)).assertIsNotDisplayed()
        repeat(4) {
            composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.READY_CONTENT)
                .performTouchInput { swipeUp() }
        }
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.row(11)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.BACK_ACTION).assertIsDisplayed()
    }

    @Test
    fun emptyStateWithLongReadyPreviewSupportsGestureScrolling() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = emptyStateWithLongPreview(),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("OCR review").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No OCR evidence").assertCountEquals(0)
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactTeam(1))
            .assertTextEquals("Slot - Not matched | Team name - Not matched")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(11)).assertIsNotDisplayed()
        repeat(4) {
            composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.EMPTY_CONTENT)
                .performTouchInput { swipeUp() }
        }
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(11)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.BACK_ACTION).assertIsDisplayed()
    }

    @Test
    fun compactRowDisplaysPlacementTeamAndPlayersInVisualSlotOrder() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        preview = compactPreview(),
                        teamNamesBySlot = mapOf(5 to "ETR ESPORTS"),
                        rows = defaultReadyRows().map { row ->
                            if (row.rowIndex == 0) row.copy(suggestedTeamSlotDisplayValue = "5") else row
                        },
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlacement(1))
            .assertTextEquals("Position - 1")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactTeam(1))
            .assertTextEquals("Slot - 5 | Team name - ETR ESPORTS")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(1, 1))
            .assertTextEquals("1. Player One - [2]")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(1, 3))
            .assertTextEquals("3. Player Three - [8]")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(1, 2))
            .assertTextEquals("2. Player Two - [7]")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(1, 4))
            .assertTextEquals("4. Player Four - [8]")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayerRow(1, 1)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayerRow(1, 2)).assertIsDisplayed()
    }

    @Test
    fun unavailableFallbackUsesExpectedPositionHeadingAndBlankPlacement() {
        val rows = defaultReadyRows().map { row ->
            if (row.rowIndex in 10..11) {
                row.copy(
                    detectedPlacementDisplayValue = "Unavailable",
                    placementStatusLabel = "Manual correction required",
                    detectedKillDisplayValue = "Unavailable",
                    killStatusLabel = "Manual correction required",
                    detectedPlayerNameEvidenceLabel = "Unavailable",
                    playerNameStatusLabel = "Manual correction required",
                    suggestedTeamSlotDisplayValue = "Unavailable",
                    confidenceScoreDisplayValue = "Unavailable",
                    confidenceTierLabel = "Unavailable",
                    assignmentSafetyStatusLabel = "Unavailable",
                    topThreeSuggestionsSummary = listOf("No suggestions"),
                    warningLabels = emptyList(),
                    blockerLabels = listOf("OCR evidence unavailable; manual correction required"),
                    severity = MatchOcrReviewSeverity.BLOCKING,
                    originalParsedPlacementValue = null,
                    originalParsedKillValue = null,
                    originalSuggestedTeamSlot = null,
                )
            } else {
                row
            }
        }
        val draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)

        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(rows = rows, correctionDraft = draft),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlacement(11))
            .performScrollTo()
            .assertTextEquals("Position - 11")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, 1))
            .assertTextEquals("1. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, 3))
            .assertTextEquals("3. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, 2))
            .assertTextEquals("2. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(11, 4))
            .assertTextEquals("4. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(10))
            .performScrollTo()
            .run {
                assertEquals(
                    "",
                    fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
                )
            }
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlacement(12))
            .performScrollTo()
            .assertTextEquals("Position - 12")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(12, 1))
            .assertTextEquals("1. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(12, 3))
            .assertTextEquals("3. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(12, 2))
            .assertTextEquals("2. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactPlayer(12, 4))
            .assertTextEquals("4. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(11))
            .performScrollTo()
            .run {
                assertEquals(
                    "",
                    fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
                )
            }
        composeTestRule.onAllNodesWithText("[Unavailable]").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("[?]").assertCountEquals(0)
    }

    @Test
    fun compactRowsDistinguishUnnamedAndUnmatchedTeams() {
        val rows = defaultReadyRows().map { row ->
            when (row.rowIndex) {
                0, 1 -> row.copy(suggestedTeamSlotDisplayValue = "5")
                2 -> row.copy(suggestedTeamSlotDisplayValue = "Unavailable")
                else -> row
            }
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        preview = compactPreview(1..3),
                        teamNamesBySlot = mapOf(5 to ""),
                        rows = rows,
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactTeam(1))
            .assertTextEquals("Slot - 5 | Team name - Not named")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactTeam(2))
            .assertTextEquals("Slot - 5 | Team name - Not named")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactTeam(3))
            .assertTextEquals("Slot - Not matched | Team name - Not matched")
    }

    @Test
    fun lobbyPlayersAppearBeforeResultRowsInDeterministicPlayerOrder() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        preview = compactPreview(1..1),
                        teamNamesBySlot = mapOf(1 to "ABC ESPORTS"),
                        lobbyPlayers = listOf(
                            MatchOcrReviewLobbySlotUiState(
                                slotNumber = 1,
                                players = listOf(
                                    MatchOcrReviewLobbyPlayerUiState(1, "Player One"),
                                    MatchOcrReviewLobbyPlayerUiState(2, "Player Two"),
                                    MatchOcrReviewLobbyPlayerUiState(3, "Player Three"),
                                    MatchOcrReviewLobbyPlayerUiState(4, null),
                                ),
                            ),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Lobby Players").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(1)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(1, 1))
            .assertTextEquals("1. Player One")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(1, 3))
            .assertTextEquals("3. Player Three")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(1, 2))
            .assertTextEquals("2. Player Two")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbyPlayer(1, 4))
            .assertTextEquals("4. Not detected")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1)).assertIsDisplayed()
    }

    @Test
    fun emptyUsefulPreviewUsesPersistedLobbyTeamNamesAndBlankFallback() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = MatchOcrReviewUiState.Empty(
                        tournamentId = "synthetic-tournament",
                        matchId = "synthetic-match",
                        matchResultOcrPreview = compactPreview(1..1),
                        teamNamesBySlot = mapOf(1 to "ETR ESPORTS", 2 to ""),
                        lobbyPlayers = listOf(
                            MatchOcrReviewLobbySlotUiState(1, emptyList()),
                            MatchOcrReviewLobbySlotUiState(2, emptyList()),
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(1))
            .assertTextContains("Slot - 1 | Team name - ETR ESPORTS")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.lobbySlot(2))
            .assertTextContains("Slot - 2 | Team name - Not named")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.compactRow(1)).assertIsDisplayed()
    }

    @Test
    fun rawOcrAndMatchingDiagnosticsAreHiddenFromCompactRows() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(preview = compactPreview()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("MATCH_RESULT_UPPER").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("MATCH_RESULT_LOWER").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Roles:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("placement=").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Confidence:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Suggested slot:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Rank 1:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Safety:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Warning:").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Blocker:").assertCountEquals(0)
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
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.CORRECTION_ROOT).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.placementInput(0)).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZATION_SUMMARY).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION).assertCountEquals(0)
    }

    @Test
    fun editableCorrectionInputsDisplayDraftValues() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.CORRECTION_ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .performScrollTo()
            .assertTextContains("1")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.killsInput(0))
            .assertTextContains("0")
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .assertTextContains("1")
    }

    @Test
    fun placementEditCallbackFiresWithRowIndexAndValue() {
        var callbackValue: Pair<Int, String>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = correctionDraft {
                            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(it, 0, "")
                        },
                    ),
                    onBack = {},
                    onPlacementChanged = { rowIndex, value -> callbackValue = rowIndex to value },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.placementInput(0))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("7"))
            }

        composeTestRule.runOnIdle {
            assertEquals(0 to "7", callbackValue)
        }
    }

    @Test
    fun killsEditCallbackFiresWithRowIndexAndValue() {
        var callbackValue: Pair<Int, String>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = correctionDraft {
                            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(it, 0, "")
                        },
                    ),
                    onBack = {},
                    onKillsChanged = { rowIndex, value -> callbackValue = rowIndex to value },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.killsInput(0))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("5"))
            }

        composeTestRule.runOnIdle {
            assertEquals(0 to "5", callbackValue)
        }
    }

    @Test
    fun teamSlotEditCallbackFiresWithRowIndexAndValue() {
        var callbackValue: Pair<Int, String>? = null
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = correctionDraft {
                            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(it, 0, "")
                        },
                    ),
                    onBack = {},
                    onAssignedTeamSlotChanged = { rowIndex, value -> callbackValue = rowIndex to value },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.teamSlotInput(0))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetText) {
                it(AnnotatedString("6"))
            }

        composeTestRule.runOnIdle {
            assertEquals(0 to "6", callbackValue)
        }
    }

    @Test
    fun correctionBlockerLabelsAreDisplayed() {
        val draftWithBlockers = correctionDraft { draft ->
            val duplicatePlacement = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, 1, "1")
            val duplicateTeamSlot = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
                duplicatePlacement,
                2,
                "1",
            )
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(duplicateTeamSlot, 3, "-1")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = draftWithBlockers),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.rowBlocker(1)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.rowBlocker(2)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.rowBlocker(3)).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Blocker: Placement is duplicated.").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Blocker: Team slot is duplicated.").assertCountEquals(2)
        composeTestRule.onNodeWithText("Blocker: Kills cannot be negative.").assertIsDisplayed()
    }

    @Test
    fun dirtyMarkerAndWarningLabelsAreDisplayed() {
        val dirtyDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = dirtyDraft),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.rowDirty(0)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Draft changed").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.rowWarning(0)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Warning: Kills changed from OCR value.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Correction draft has unsaved in-memory changes.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun resetRowAndResetAllCallbacksFire() {
        var resetRowIndex: Int? = null
        var resetAllCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                    onResetRowCorrection = { resetRowIndex = it },
                    onResetAllCorrections = { resetAllCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.resetRow(0))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.RESET_ALL)
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, resetRowIndex)
            assertEquals(1, resetAllCount)
        }
    }

    @Test
    fun correctionModeDoesNotExposeSaveFinalizeOrPersistenceControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Assign team").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Sync").assertCountEquals(0)
    }

    @Test
    fun finalizationControlsAreVisibleWhenCorrectionDraftExists() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZATION_SUMMARY)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION).assertIsDisplayed()
        composeTestRule.onNodeWithText("Finalize corrected match").assertIsDisplayed()
    }

    @Test
    fun finalizeActionCallbackFiresWhenCorrectionDraftIsAllowed() {
        var finalizeCount = 0
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                    onFinalizeOcrCorrection = { finalizeCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION)
            .performScrollTo()
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, finalizeCount)
        }
    }

    @Test
    fun finalizationIsDisabledAndBlockerCountVisibleWhenCorrectionDraftHasBlockers() {
        val blockedDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, 0, "")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = blockedDraft),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_BLOCKED_LABEL)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Finalization blocked: 1 blocker(s).").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION).assertIsNotEnabled()
    }

    @Test
    fun finalizationWarningCountIsVisibleWhenWarningsExist() {
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = warningDraft),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_COUNT)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Warnings requiring confirmation: 1.").assertIsDisplayed()
    }

    @Test
    fun warningConfirmationDialogIsVisibleAndActionsFire() {
        var confirmCount = 0
        var dismissCount = 0
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = warningDraft,
                        finalization = MatchOcrReviewFinalizationUiState(showWarningConfirmation = true),
                    ),
                    onBack = {},
                    onConfirmFinalizeWarnings = { confirmCount += 1 },
                    onDismissFinalizeWarnings = { dismissCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_WARNING_DIALOG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.CONFIRM_FINALIZE_WARNINGS).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, confirmCount)
            assertEquals(0, dismissCount)
        }
    }

    @Test
    fun dismissWarningConfirmationActionFires() {
        var dismissCount = 0
        val warningDraft = correctionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 0, "9")
        }
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = warningDraft,
                        finalization = MatchOcrReviewFinalizationUiState(showWarningConfirmation = true),
                    ),
                    onBack = {},
                    onDismissFinalizeWarnings = { dismissCount += 1 },
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.DISMISS_FINALIZE_WARNINGS).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun finalizationSuccessStateIsVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = correctionDraft(),
                        finalization = MatchOcrReviewFinalizationUiState(isFinalized = true),
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZATION_SUCCESS)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrected OCR match finalized.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZE_ACTION).assertIsNotEnabled()
    }

    @Test
    fun finalizationErrorStateIsVisible() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(
                        correctionDraft = correctionDraft(),
                        finalization = MatchOcrReviewFinalizationUiState(
                            error = MatchOcrReviewFinalizationError.FINALIZATION_FAILED,
                        ),
                    ),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(MatchOcrReviewTestTags.FINALIZATION_ERROR)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("The corrected match could not be finalized.").assertIsDisplayed()
    }

    @Test
    fun correctionModeDoesNotExposeOutOfScopeFinalizationAdjacentControls() {
        composeTestRule.setContent {
            RankForgeTheme {
                MatchOcrReviewScreen(
                    uiState = readyState(correctionDraft = correctionDraft()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Export").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Sync").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Retry OCR").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Edit roster").assertCountEquals(0)
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

    private fun readyState(
        correctionDraft: MatchOcrReviewCorrectionDraft? = null,
        finalization: MatchOcrReviewFinalizationUiState = MatchOcrReviewFinalizationUiState(),
        preview: MatchResultOcrPreviewUiState = MatchResultOcrPreviewUiState.NotRequested,
        teamNamesBySlot: Map<Int, String> = emptyMap(),
        lobbyPlayers: List<MatchOcrReviewLobbySlotUiState> = emptyList(),
        rows: List<MatchOcrReviewRowUiState> = defaultReadyRows(),
    ): MatchOcrReviewUiState.Ready = MatchOcrReviewUiState.Ready(
        tournamentId = "synthetic-tournament",
        matchId = "synthetic-match",
        matchDisplayLabel = "Synthetic Match",
        rowCount = rows.size,
        rows = rows,
        blockerCount = 1,
        warningCount = 1,
        safeRowCount = 10,
        manualRequiredRowCount = 1,
        reviewRequiredRowCount = 1,
        manualReviewRequired = true,
        hasUnavailableEvidence = false,
        correctionDraft = correctionDraft,
        finalization = finalization,
        matchResultOcrPreview = preview,
        teamNamesBySlot = teamNamesBySlot,
        lobbyPlayers = lobbyPlayers,
    )

    private fun defaultReadyRows(): List<MatchOcrReviewRowUiState> = (0..11).map { rowIndex ->
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
                originalParsedPlacementValue = if (rowIndex == 2) null else rowIndex + 1,
                originalParsedKillValue = if (rowIndex == 0) 3 else rowIndex,
                originalSuggestedTeamSlot = rowIndex + 1,
            )
        }

    private fun emptyStateWithLongPreview(): MatchOcrReviewUiState.Empty =
        MatchOcrReviewUiState.Empty(
            tournamentId = "synthetic-tournament",
            matchId = "synthetic-match",
            matchResultOcrPreview = MatchResultOcrPreviewUiState.Ready(
                roles = listOf(
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                ),
                rows = (1..11).map { position ->
                    MatchResultOcrPreviewRowUiState(
                        position = position,
                        role = if (position <= 6) {
                            MatchResultScreenshotRole.MATCH_RESULT_UPPER
                        } else {
                            MatchResultScreenshotRole.MATCH_RESULT_LOWER
                        },
                        sourceLabel = "Synthetic OCR row $position",
                        placementText = position.toString(),
                        slots = (1..4).map { slot ->
                            MatchResultOcrPreviewSlotUiState(
                                slot = slot,
                                playerText = "Player $position-$slot",
                                playerOcrText = "Player $position-$slot",
                                playerStatusLabel = "Accepted",
                                killText = slot.toString(),
                                killOcrText = slot.toString(),
                                killStatusLabel = "Accepted",
                            )
                        },
                    )
                },
                ignoredLowerRows = emptyList(),
                manualReviewRows = emptyList(),
            ),
        )

    private fun compactPreview(positions: IntRange = 1..12): MatchResultOcrPreviewUiState.Ready =
        MatchResultOcrPreviewUiState.Ready(
            roles = listOf(
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            ),
            rows = positions.map { position ->
                MatchResultOcrPreviewRowUiState(
                    position = position,
                    role = if (position <= 10) {
                        MatchResultScreenshotRole.MATCH_RESULT_UPPER
                    } else {
                        MatchResultScreenshotRole.MATCH_RESULT_LOWER
                    },
                    sourceLabel = "PREVIEW",
                    placementText = position.toString(),
                    slots = listOf(
                        MatchResultOcrPreviewSlotUiState(1, "Player One", "", "", "2", "", ""),
                        MatchResultOcrPreviewSlotUiState(2, "Player Two", "", "", "7", "", ""),
                        MatchResultOcrPreviewSlotUiState(3, "Player Three", "", "", "8", "", ""),
                        MatchResultOcrPreviewSlotUiState(4, "Player Four", "", "", "8", "", ""),
                    ),
                )
            },
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )

    private fun correctionDraft(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft = { it },
    ): MatchOcrReviewCorrectionDraft =
        transform(MatchOcrReviewCorrectionDraftReducer.createInitialDraft(correctionRows()))

    private fun correctionRows(): List<MatchOcrReviewRowUiState> =
        (0..11).map { rowIndex ->
            MatchOcrReviewRowUiState(
                rowIndex = rowIndex,
                expectedPlacementLabel = (rowIndex + 1).toString(),
                detectedPlacementDisplayValue = (rowIndex + 1).toString(),
                placementStatusLabel = "Accepted",
                detectedKillDisplayValue = rowIndex.toString(),
                killStatusLabel = "Accepted",
                detectedPlayerNameEvidenceLabel = "Synthetic Unit ${rowIndex + 1}",
                playerNameStatusLabel = "Accepted",
                suggestedTeamSlotDisplayValue = (rowIndex + 1).toString(),
                confidenceScoreDisplayValue = "96",
                confidenceTierLabel = "Automatic candidate",
                assignmentSafetyStatusLabel = "Safe automatic assignment",
                topThreeSuggestionsSummary = listOf(
                    "Rank 1: Slot ${rowIndex + 1}, confidence 96, matches 4, coverage 100",
                ),
                warningLabels = emptyList(),
                blockerLabels = emptyList(),
                severity = MatchOcrReviewSeverity.INFORMATIONAL,
                originalParsedPlacementValue = rowIndex + 1,
                originalParsedKillValue = rowIndex,
                originalSuggestedTeamSlot = rowIndex + 1,
            )
        }
}
