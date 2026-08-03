package com.hoggamers.rankforge.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationIssue
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationIssueType
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationSeverity
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrFailure
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RosterOcrReviewSectionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingStateIsControlled() {
        setSection(RosterOcrReviewUiState.LoadingTeamContext(TOURNAMENT_ID))

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading OCR roster review")
            .assertIsDisplayed()
    }

    @Test
    fun readyStateOffersProcessingAction() {
        var calls = 0
        setSection(
            RosterOcrReviewUiState.ReadyToProcess(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
            ),
            onStartProcessing = { calls++ },
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_START_PROCESSING_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, calls) }
    }

    @Test
    fun processingStateShowsProgressAndNoDuplicateStartAction() {
        setSection(
            RosterOcrReviewUiState.Processing(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
            ),
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_PROCESSING_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_START_PROCESSING_TEST_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun reviewingStateRendersAllTwelveSlotsAndSeventyTwoFields() {
        setSection(reviewingState())

        (1..12).forEach { slotNumber ->
            composeTestRule.onNodeWithTag(rosterOcrReviewSlotTestTag(slotNumber))
                .performScrollTo()
                .assertIsDisplayed()
            (1..6).forEach { playerRowIndex ->
                composeTestRule.onNodeWithTag(
                    rosterOcrReviewPlayerTestTag(slotNumber, playerRowIndex),
                ).performScrollTo().assertIsDisplayed()
            }
        }
        composeTestRule.onAllNodesWithTag(ROSTER_OCR_REVIEW_SECTION_TEST_TAG)
            .assertCountEquals(1)
    }

    @Test
    fun reviewingStateRendersRoomTeamNamesAsText() {
        setSection(reviewingState())

        (1..12).forEach { slotNumber ->
            composeTestRule.onNodeWithTag(rosterOcrReviewTeamTestTag(slotNumber))
                .performScrollTo()
                .assertTextContains("Room Team $slotNumber")
        }
    }

    @Test
    fun reviewingStateRendersSixPlayerFieldsIncludingManualRows() {
        setSection(reviewingState())

        composeTestRule.onAllNodesWithText("Player 5 (manual)", substring = false)
            .assertCountEquals(12)
        composeTestRule.onAllNodesWithText("Player 6 (manual)", substring = false)
            .assertCountEquals(12)
    }

    @Test
    fun unchangedOcrRowShowsOriginalCandidateSeparately() {
        setSection(reviewingState())

        composeTestRule.onNodeWithTag(rosterOcrReviewOriginalCandidateTestTag(1, 1))
            .performScrollTo()
            .assertTextContains("OCR-1-1", substring = false)
    }

    @Test
    fun correctedRowRetainsOriginalCandidateAndShowsCorrectionIndicator() {
        val correctedDraft = draft().copy(
            slots = draft().slots.map { slot ->
                if (slot.slotNumber != 1) slot else slot.copy(
                    players = slot.players.map { player ->
                        if (player.playerRowIndex != 1) player else player.copy(
                            draftValue = "Corrected player",
                        )
                    },
                )
            },
        )
        setSection(reviewingState(draft = correctedDraft))

        composeTestRule.onNodeWithTag(rosterOcrReviewOriginalCandidateTestTag(1, 1))
            .performScrollTo().assertTextContains("OCR-1-1", substring = false)
        composeTestRule.onNodeWithTag(rosterOcrReviewCorrectionIndicatorTestTag(1, 1))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(rosterOcrReviewPlayerTestTag(1, 1))
            .performScrollTo().assertTextContains("Corrected player", substring = false)
    }

    @Test
    fun blankOcrRowShowsNoUsableCandidate() {
        setSection(
            reviewingState(
                draft = draft(
                    sourceStatus = RosterCandidateParseStatus.MISSING,
                    blankOriginalOcrRow = 1,
                ),
            ),
        )

        composeTestRule.onNodeWithTag(rosterOcrReviewOriginalCandidateTestTag(1, 1))
            .performScrollTo()
            .assertTextContains("No usable OCR candidate.", substring = false)
    }

    @Test
    fun parsedSourceStatusUsesControlledMessage() {
        setSection(reviewingState())

        composeTestRule.onNodeWithTag(rosterOcrReviewSourceStatusTestTag(1, 1))
            .performScrollTo()
            .assertTextContains(
                "OCR candidate parsed; review before confirming.",
                substring = false,
            )
    }

    @Test
    fun ambiguousUncertainAndFailedSourceStatusesRequireManualReview() {
        val cases = listOf(
            RosterCandidateParseStatus.AMBIGUOUS to
                "OCR candidate is ambiguous; manual review required.",
            RosterCandidateParseStatus.UNCERTAIN to
                "OCR candidate is uncertain; manual review required.",
            RosterCandidateParseStatus.INPUT_FAILURE to
                "OCR candidate input failed; manual review required.",
        )
        var state by mutableStateOf(reviewingState(draft = draft(sourceStatus = cases.first().first)))
        setSection(state = state, stateProvider = { state })

        cases.forEach { (status, message) ->
            state = reviewingState(draft = draft(sourceStatus = status))
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(rosterOcrReviewSourceStatusTestTag(1, 1))
                .performScrollTo().assertTextContains(message, substring = false)
        }
    }

    @Test
    fun slotSpecificOcrIssuesAreVisible() {
        setSection(reviewingState(draft = draft(slotIssueCount = 2)))

        composeTestRule.onNodeWithTag(rosterOcrReviewSlotIssueTestTag(1))
            .performScrollTo()
            .assertTextContains("Slot 1 OCR issue count: 2.", substring = false)
    }

    @Test
    fun manualRowsShowOnlyManualEvidenceState() {
        setSection(reviewingState())

        (5..6).forEach { row ->
            composeTestRule.onNodeWithTag(rosterOcrReviewManualOnlyTestTag(1, row))
                .performScrollTo()
                .assertTextContains("Manual entry only.", substring = false)
            composeTestRule.onNodeWithTag(rosterOcrReviewOriginalCandidateTestTag(1, row))
                .assertDoesNotExist()
            composeTestRule.onNodeWithTag(rosterOcrReviewSourceStatusTestTag(1, row))
                .assertDoesNotExist()
        }
    }

 @Test
fun playerUpdateInvokesSlotAndRowCallback() {
    var correctedUpdate: Triple<Int, Int, String>? = null
    setSection(
        reviewingState(),
        onUpdatePlayerName = { slot, row, value ->
            if (value == "Corrected player") {
                correctedUpdate = Triple(slot, row, value)
            }
        },
    )

    composeTestRule.onNodeWithTag(rosterOcrReviewPlayerTestTag(3, 2))
        .performScrollTo()
        .performTextReplacement("Corrected player")

    composeTestRule.runOnIdle {
        assertEquals(
            Triple(3, 2, "Corrected player"),
            correctedUpdate,
        )
    }
}
    @Test
    fun resetPlayerInvokesSlotAndRowCallback() {
        var reset: Pair<Int, Int>? = null
        setSection(
            reviewingState(),
            onResetPlayerCorrection = { slot, row -> reset = slot to row },
        )

        composeTestRule.onNodeWithTag(rosterOcrReviewResetPlayerTestTag(4, 1))
            .performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(4 to 1, reset) }
    }

    @Test
    fun resetSlotInvokesSlotCallback() {
        var resetSlot = 0
        setSection(
            reviewingState(),
            onResetSlotCorrections = { resetSlot = it },
        )

        composeTestRule.onNodeWithTag(rosterOcrReviewResetSlotTestTag(8))
            .performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(8, resetSlot) }
    }

    @Test
    fun resetAllInvokesCallback() {
        var resetCount = 0
        setSection(reviewingState(), onResetAllCorrections = { resetCount++ })

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_RESET_ALL_TEST_TAG)
            .performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(1, resetCount) }
    }

    @Test
    fun validationBlockerDisablesConfirmation() {
        setSection(reviewingState(draft = draft(blocked = true)))

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_VALIDATION_TEST_TAG)
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG)
            .performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun controlledProcessingFailureUsesSafeTypedMessage() {
        setSection(
            RosterOcrReviewUiState.ReadyToProcess(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                processingFailure = RosterOcrReviewProcessingFailure.Controlled(
                    ProcessRosterOcrFailure.InvalidTournamentContext,
                ),
            ),
        )

        composeTestRule.onNodeWithText(
            "The tournament context is invalid for OCR review.",
        ).assertIsDisplayed()
    }

    @Test
    fun evidenceIssuesAreShownWithoutRawOcrDetails() {
        setSection(reviewingState(evidence = evidence(withWarning = true)))

        composeTestRule.onNodeWithText("OCR evidence includes 1 issue(s).").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Retained OCR evidence is available for review; raw OCR details are not displayed here.",
        ).assertIsDisplayed()
    }

    @Test
    fun finalValidationShowsSlotAndPlayerInformation() {
        setSection(
            reviewingState(
                draft = draft().copy(
                    finalValidation = RosterOcrReviewDraftValidation(
                        issues = listOf(
                            RosterOcrReviewDraftIssue(
                                type = RosterOcrReviewDraftIssueType.DUPLICATE_PLAYER_NAME,
                                slotNumber = 7,
                                playerRowIndex = 5,
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText(
            "Slot 7, player 5 issue: Player name is duplicated.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun requestConfirmationShowsTwoStepDialog() {
        var state by mutableStateOf(reviewingState())
        setSection(
            state = state,
            stateProvider = { state },
            onRequestConfirmation = {
                state = (state as RosterOcrReviewUiState.Reviewing).copy(
                    confirmation = RosterOcrReviewConfirmationState.Requested,
                )
            },
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG)
            .performScrollTo().performClick()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun blockedDraftCannotShowConfirmationDialog() {
        setSection(
            reviewingState(
                draft = draft(blocked = true),
                confirmation = RosterOcrReviewConfirmationState.Requested,
            ),
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG)
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun confirmationDialogCanBeDismissed() {
        var state by mutableStateOf(
            reviewingState(confirmation = RosterOcrReviewConfirmationState.Requested),
        )
        setSection(
            state = state,
            stateProvider = { state },
            onDismissConfirmation = {
                state = (state as RosterOcrReviewUiState.Reviewing).copy(
                    confirmation = RosterOcrReviewConfirmationState.NotRequested,
                )
            },
        )

        composeTestRule.onNodeWithText("Continue reviewing").performClick()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun confirmationDialogInvokesFinalConfirmCallback() {
        var confirmCount = 0
        setSection(
            reviewingState(confirmation = RosterOcrReviewConfirmationState.Requested),
            onConfirmReplacement = { confirmCount++ },
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_CONFIRM_TEST_TAG).performClick()
        composeTestRule.runOnIdle { assertEquals(1, confirmCount) }
    }

    @Test
    fun abandonInvokesCallback() {
        var abandonCount = 0
        setSection(reviewingState(), onAbandonReview = { abandonCount++ })

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_ABANDON_TEST_TAG)
            .performScrollTo().performClick()
        composeTestRule.runOnIdle { assertEquals(1, abandonCount) }
    }

    @Test
    fun localReplacementProgressIsVisibleAndEditingIsDisabled() {
        setSection(
            reviewingState(localReplacement = RosterOcrLocalReplacementState.InProgress),
        )

        composeTestRule.onNodeWithText("Saving reviewed roster locally.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(rosterOcrReviewPlayerTestTag(1, 1))
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(rosterOcrReviewResetPlayerTestTag(1, 1))
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(rosterOcrReviewResetSlotTestTag(1))
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_RESET_ALL_TEST_TAG)
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_ABANDON_TEST_TAG)
            .performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG)
            .performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun inProgressDoesNotShowConfirmationDialog() {
        setSection(
            reviewingState(
                confirmation = RosterOcrReviewConfirmationState.Requested,
                localReplacement = RosterOcrLocalReplacementState.InProgress,
            ),
        )

        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun localReplacementFailureIsControlled() {
        val cases = listOf(
            RosterOcrReviewLocalReplacementError.DRAFT_BLOCKED to
                "The reviewed roster is blocked by validation issues.",
            RosterOcrReviewLocalReplacementError.TOURNAMENT_NOT_FOUND to
                "The tournament was not found locally. The roster was not changed.",
            RosterOcrReviewLocalReplacementError.INVALID_CANDIDATE to
                "The reviewed roster candidate is invalid. The roster was not changed.",
            RosterOcrReviewLocalReplacementError.BLOCKED_BY_EXISTING_MATCHES to
                "The reviewed roster is blocked by existing matches. The roster was not changed.",
            RosterOcrReviewLocalReplacementError.UNEXPECTED_FAILURE to
                "The reviewed roster could not be saved locally because of an unexpected failure.",
        )

        var state by mutableStateOf(
            reviewingState(
                localReplacement = RosterOcrLocalReplacementState.Failed(cases.first().first),
            ),
        )
        setSection(
            state = state,
            stateProvider = { state },
        )
        cases.forEach { (error, message) ->
            state = reviewingState(localReplacement = RosterOcrLocalReplacementState.Failed(error))
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun returnedCloudFailureIsControlledAfterLocalCommit() {
        setSection(
            RosterOcrReviewUiState.LocalReplacementCommitted(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudSynchronization = RosterOcrCloudSynchronizationState.Failed(
                    cloudResult(TournamentRosterCloudReplacementResult.NetworkFailure),
                ),
            ),
        )

        composeTestRule.onNodeWithText(
            "Cloud synchronization could not reach the cloud. The local roster was kept.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unexpectedCloudFailureIsControlledAfterLocalCommit() {
        setSection(
            RosterOcrReviewUiState.LocalReplacementCommitted(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudSynchronization = RosterOcrCloudSynchronizationState.UnexpectedFailure,
            ),
        )

        composeTestRule.onNodeWithText(
            "Cloud synchronization encountered an unexpected failure. The local roster was kept.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun eachReturnedCloudResultUsesDistinctControlledMessage() {
        val cases = listOf(
            TournamentRosterCloudReplacementResult.AuthenticationRequired to
                "Cloud synchronization requires authentication. The local roster was kept.",
            TournamentRosterCloudReplacementResult.NetworkFailure to
                "Cloud synchronization could not reach the cloud. The local roster was kept.",
            TournamentRosterCloudReplacementResult.ValidationFailure to
                "Cloud rejected the reviewed roster as invalid. The local roster was kept.",
            TournamentRosterCloudReplacementResult.AuthorizationFailure to
                "Cloud authorization denied the reviewed roster. The local roster was kept.",
            TournamentRosterCloudReplacementResult.Conflict(RevisionConflict.MissingRevision) to
                "Cloud data changed before synchronization. The local roster was kept.",
            TournamentRosterCloudReplacementResult.BlockedByExistingMatches to
                "Cloud synchronization is blocked by existing matches. The local roster was kept.",
            TournamentRosterCloudReplacementResult.UnknownFailure to
                "Cloud synchronization failed for an unknown reason. The local roster was kept.",
        )

        var state by mutableStateOf(committedCloudFailureState(cases.first().first))
        setSection(
            state = state,
            stateProvider = { state },
        )
        cases.forEach { (result, message) ->
            state = committedCloudFailureState(result)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun committedCloudProgressIsReadOnly() {
        setSection(
            RosterOcrReviewUiState.LocalReplacementCommitted(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudSynchronization = RosterOcrCloudSynchronizationState.InProgress,
            ),
        )

        composeTestRule.onNodeWithText("Synchronizing reviewed roster to cloud.")
            .performScrollTo().assertIsDisplayed()
        assertNoMutationControls()
        composeTestRule.onNodeWithTag(rosterOcrReviewPlayerTestTag(1, 1))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun committedStateHasNoEditResetOrConfirmationActions() {
        setSection(
            RosterOcrReviewUiState.LocalReplacementCommitted(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudSynchronization = RosterOcrCloudSynchronizationState.UnexpectedFailure,
            ),
        )

        assertNoMutationControls()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun completedStateHasNoEditResetOrConfirmationActions() {
        setSection(
            RosterOcrReviewUiState.Completed(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudResult = cloudResult(TournamentRosterCloudReplacementResult.Success(2)),
            ),
        )

        assertNoMutationControls()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_DIALOG_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun committedReadOnlyStateHidesCandidateEvidenceButShowsCorrectedFinalValue() {
        val correctedDraft = draft().copy(
            slots = draft().slots.map { slot ->
                if (slot.slotNumber != 1) slot else slot.copy(
                    players = slot.players.map { player ->
                        if (player.playerRowIndex != 1) player else player.copy(
                            draftValue = "Corrected final player",
                        )
                    },
                )
            },
        )
        setSection(
            RosterOcrReviewUiState.Completed(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = correctedDraft,
                cloudResult = cloudResult(TournamentRosterCloudReplacementResult.Success(2)),
            ),
        )

        assertNoMutationControls()
        composeTestRule.onNodeWithTag(rosterOcrReviewOriginalCandidateTestTag(1, 1))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(rosterOcrReviewSourceStatusTestTag(1, 1))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(rosterOcrReviewPlayerTestTag(1, 1))
            .performScrollTo()
            .assertTextContains("Player 1: Corrected final player", substring = false)
    }

    @Test
    fun rawExtractionPathsAndExceptionsAreNotDisplayed() {
        setSection(reviewingState())

        composeTestRule.onNodeWithText("raw-extraction-payload", substring = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("/private/ocr/source/path", substring = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("IllegalStateException", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun completedSuccessIsVisible() {
        setSection(
            RosterOcrReviewUiState.Completed(
                tournamentId = TOURNAMENT_ID,
                teamSlots = teamSlots(),
                evidence = evidence(),
                draft = draft(),
                cloudResult = cloudResult(TournamentRosterCloudReplacementResult.Success(2)),
            ),
        )

        composeTestRule.onNodeWithText("Reviewed roster saved locally.")
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Reviewed roster synchronized to cloud.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableStateUsesControlledMessage() {
        setSection(
            RosterOcrReviewUiState.Unavailable(
                tournamentId = TOURNAMENT_ID,
                failure = RosterOcrReviewLoadFailure.INCOMPLETE_TEAM_CONTEXT,
            ),
        )

        composeTestRule.onNodeWithText(
            "OCR roster review is unavailable. Continue with the manual roster workflow.",
        ).assertIsDisplayed()
    }

    private fun setSection(
        state: RosterOcrReviewUiState,
        stateProvider: (() -> RosterOcrReviewUiState)? = null,
        onStartProcessing: () -> Unit = {},
        onUpdatePlayerName: (Int, Int, String) -> Unit = { _, _, _ -> },
        onResetPlayerCorrection: (Int, Int) -> Unit = { _, _ -> },
        onResetSlotCorrections: (Int) -> Unit = {},
        onResetAllCorrections: () -> Unit = {},
        onAbandonReview: () -> Unit = {},
        onRequestConfirmation: () -> Unit = {},
        onDismissConfirmation: () -> Unit = {},
        onConfirmReplacement: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RankForgeTheme {
                Column(modifier = androidx.compose.ui.Modifier.verticalScroll(rememberScrollState())) {
                    RosterOcrReviewSection(
                        state = stateProvider?.invoke() ?: state,
                        onStartProcessing = onStartProcessing,
                        onUpdatePlayerName = onUpdatePlayerName,
                        onResetPlayerCorrection = onResetPlayerCorrection,
                        onResetSlotCorrections = onResetSlotCorrections,
                        onResetAllCorrections = onResetAllCorrections,
                        onAbandonReview = onAbandonReview,
                        onRequestConfirmation = onRequestConfirmation,
                        onDismissConfirmation = onDismissConfirmation,
                        onConfirmReplacement = onConfirmReplacement,
                    )
                }
            }
        }
    }

    private fun assertNoMutationControls() {
        composeTestRule.onNodeWithTag(rosterOcrReviewResetPlayerTestTag(1, 1))
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(rosterOcrReviewResetSlotTestTag(1)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_RESET_ALL_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_ABANDON_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_REQUEST_CONFIRMATION_TEST_TAG)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(ROSTER_OCR_REVIEW_CONFIRM_TEST_TAG).assertDoesNotExist()
    }

    private fun reviewingState(
        evidence: ProcessRosterOcrEvidence = evidence(),
        draft: RosterOcrReviewDraft = draft(evidence = evidence),
        confirmation: RosterOcrReviewConfirmationState =
            RosterOcrReviewConfirmationState.NotRequested,
        localReplacement: RosterOcrLocalReplacementState = RosterOcrLocalReplacementState.Ready,
    ) = RosterOcrReviewUiState.Reviewing(
        tournamentId = TOURNAMENT_ID,
        teamSlots = teamSlots(),
        evidence = evidence,
        draft = draft,
        confirmation = confirmation,
        localReplacement = localReplacement,
    )

    private fun teamSlots() = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID).map {
        it.copy(teamName = "Room Team ${it.slotNumber}")
    }

    private fun evidence(withWarning: Boolean = false) = ProcessRosterOcrEvidence(
        rawExtractions = emptyList(),
        parsedCandidates = RosterCandidateParseResult(emptyList(), emptyList()),
        associatedCandidates = RosterSlotAssociationResult(emptyList(), emptyList()),
        validation = RosterOcrValidationResult(
            status = if (withWarning) {
                RosterOcrValidationStatus.NEEDS_MANUAL_REVIEW
            } else {
                RosterOcrValidationStatus.READY_FOR_REVIEW
            },
            slotResults = emptyList(),
            globalIssues = if (withWarning) {
                listOf(
                    RosterOcrValidationIssue(
                        severity = RosterOcrValidationSeverity.WARNING,
                        type = RosterOcrValidationIssueType.TEAM_NAME_UNAVAILABLE,
                    ),
                )
            } else {
                emptyList()
            },
        ),
    )

    private fun draft(
        evidence: ProcessRosterOcrEvidence = evidence(),
        blocked: Boolean = false,
        sourceStatus: RosterCandidateParseStatus = RosterCandidateParseStatus.PARSED,
        blankOriginalOcrRow: Int? = null,
        slotIssueCount: Int = 0,
    ) = RosterOcrReviewDraft(
        tournamentId = TOURNAMENT_ID,
        slots = (1..12).map { slotNumber ->
            RosterOcrReviewSlotDraft(
                slotNumber = slotNumber,
                currentTeamName = "Room Team $slotNumber",
                sourceIssues = if (slotNumber == 1) {
                    List(slotIssueCount) {
                        RosterOcrValidationIssue(
                            severity = RosterOcrValidationSeverity.WARNING,
                            type = RosterOcrValidationIssueType.TEAM_NAME_UNAVAILABLE,
                            tournamentSlotNumber = slotNumber,
                        )
                    }
                } else {
                    emptyList()
                },
                players = (1..6).map { row ->
                    val isManualOnly = row >= 5
                    val originalOcrValue = if (isManualOnly || row == blankOriginalOcrRow) {
                        ""
                    } else {
                        "OCR-$slotNumber-$row"
                    }
                    RosterOcrReviewPlayerDraft(
                        playerRowIndex = row,
                        originalOcrValue = originalOcrValue,
                        draftValue = originalOcrValue,
                        sourceCandidate = if (isManualOnly) {
                            null
                        } else {
                            sourceCandidate(
                                slotNumber = slotNumber,
                                playerRowIndex = row,
                                status = sourceStatus,
                                candidateText = originalOcrValue.ifBlank { null },
                            )
                        },
                        isManualOnly = isManualOnly,
                    )
                },
            )
        },
        originalEvidence = evidence,
        finalValidation = if (blocked) {
            RosterOcrReviewDraftValidation(
                issues = listOf(
                    RosterOcrReviewDraftIssue(
                        type = RosterOcrReviewDraftIssueType.MISSING_TEAM_NAME,
                        slotNumber = 1,
                    ),
                ),
            )
        } else {
            RosterOcrReviewDraftValidation()
        },
    )

    private fun sourceCandidate(
        slotNumber: Int,
        playerRowIndex: Int,
        status: RosterCandidateParseStatus,
        candidateText: String?,
    ) = RosterPlayerNameCandidate(
        regionIdentity = RosterRawOcrRegionIdentity(
            screenshotPosition = when (slotNumber) {
                in 1..4 -> RosterScreenshotPosition.ONE
                in 5..8 -> RosterScreenshotPosition.TWO
                else -> RosterScreenshotPosition.THREE
            },
            visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
            regionType = RosterRawOcrRegionType.PLAYER_ROW,
            playerRowIndex = playerRowIndex,
        ),
        playerRowIndex = playerRowIndex,
        status = status,
        candidateText = candidateText,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun cloudResult(result: TournamentRosterCloudReplacementResult) =
        QueueAwareActionResult(
            primaryResult = result,
            queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        )

    private fun committedCloudFailureState(result: TournamentRosterCloudReplacementResult) =
        RosterOcrReviewUiState.LocalReplacementCommitted(
            tournamentId = TOURNAMENT_ID,
            teamSlots = teamSlots(),
            evidence = evidence(),
            draft = draft(),
            cloudSynchronization = RosterOcrCloudSynchronizationState.Failed(cloudResult(result)),
        )

    private companion object {
        const val TOURNAMENT_ID = "ocr-review-test-tournament"
    }
}
