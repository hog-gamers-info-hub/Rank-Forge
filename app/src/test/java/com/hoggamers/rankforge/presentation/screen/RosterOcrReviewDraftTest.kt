package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationIssue
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationIssueType
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationSeverity
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrSlotValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterPlayerNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTournamentSlotCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTeamNameCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationStatus
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrEvidence
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterOcrReviewDraftTest {
    @Test
    fun createsTwelveOrderedSlotsAndSixOrderedRowsUsingRoomTeamNames() {
        val draft = draft()

        assertEquals((1..12).toList(), draft.slots.map { it.slotNumber })
        assertEquals((1..12).map { "room-team-$it" }, draft.slots.map { it.currentTeamName })
        assertTrue(draft.slots.all { it.players.map { player -> player.playerRowIndex } == (1..6).toList() })
        assertTrue(draft.slots.all { it.players[4].isManualOnly && it.players[5].isManualOnly })
        assertTrue(draft.slots.all { it.players.take(4).none { player -> player.isManualOnly } })
    }

    @Test
    fun teamNameEvidenceIsIgnoredAndOnlyUnambiguousParsedRowsInitialize() {
        val invalid = draft(
            evidence = evidence(
                candidates = listOf(
                    candidate(1, mapOf(1 to "parsed", 2 to "", 3 to "ambiguous")),
                ),
            ),
        )
        val slot = invalid.slots.first()

        assertEquals("room-team-1", slot.currentTeamName)
        assertEquals("parsed", slot.players[0].draftValue)
        assertEquals("", slot.players[1].draftValue)
        assertEquals("", slot.players[2].draftValue)
        assertEquals("", slot.players[3].draftValue)
        assertEquals(listOf("", ""), slot.players.takeLast(2).map { it.draftValue })
        assertEquals(RosterCandidateParseStatus.PARSED, slot.players[0].sourceStatus)
        assertEquals(RosterCandidateParseStatus.EMPTY, slot.players[1].sourceStatus)
        assertEquals(RosterCandidateParseStatus.AMBIGUOUS, slot.players[2].sourceStatus)
    }

    @Test
    fun syntheticRowsFiveAndSixAreIgnoredAndRemainManualOnly() {
        val base = candidate(1, (1..4).associateWith { row -> "ocr-1-$row" })
        val extraRows = listOf(5, 6).map { row ->
            RosterPlayerNameCandidate(
                regionIdentity = RosterRawOcrRegionIdentity(
                    screenshotPosition = RosterScreenshotPosition.ONE,
                    visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                    regionType = RosterRawOcrRegionType.SLOT_CONTENT,
                ),
                playerRowIndex = row,
                status = RosterCandidateParseStatus.PARSED,
                candidateText = "must-not-enter-draft-$row",
                failure = null,
                rawSourceResults = emptyList(),
                confidence = RawOcrConfidence.Unavailable,
            )
        }
        val intendedEvidence = evidence(
            candidates = listOf(base.copy(playerNameCandidates = base.playerNameCandidates + extraRows)) +
                (2..12).map { slot ->
                    candidate(slot, (1..4).associateWith { row -> "ocr-$slot-$row" })
                },
        )
        val draft = draft(intendedEvidence)
        val rows = draft.slots.first().players.takeLast(2)

        assertEquals(listOf("", ""), rows.map { it.originalOcrValue })
        assertEquals(listOf("", ""), rows.map { it.draftValue })
        assertTrue(rows.all { it.sourceCandidate == null && it.isManualOnly })
        assertEquals(intendedEvidence, draft.originalEvidence)
        assertEquals("manual-fifth", RosterOcrReviewDraftReducer.updatePlayerName(draft, 1, 5, "manual-fifth")
            .slots.first().players[4].draftValue)
    }

    @Test
    fun editsPreserveOriginalEvidenceAndOnlySelectedRowChanges() {
        val original = draft()
        val edited = RosterOcrReviewDraftReducer.updatePlayerName(original, 1, 1, " corrected ")

        assertEquals("ocr-1-1", edited.slots[0].players[0].originalOcrValue)
        assertEquals(" corrected ", edited.slots[0].players[0].draftValue)
        assertTrue(edited.slots[0].players[0].isDirty)
        assertEquals(original.slots[0].players.drop(1), edited.slots[0].players.drop(1))
        assertEquals(original.slots.drop(1), edited.slots.drop(1))
        assertEquals(original.originalEvidence, edited.originalEvidence)
    }

    @Test
    fun resetPlayerSlotAndAllRestoreOcrValuesWithoutUsingPersistedRoster() {
        val edited = draft()
            .let { RosterOcrReviewDraftReducer.updatePlayerName(it, 1, 1, "one") }
            .let { RosterOcrReviewDraftReducer.updatePlayerName(it, 1, 2, "two") }
            .let { RosterOcrReviewDraftReducer.updatePlayerName(it, 2, 1, "other") }
        val playerReset = RosterOcrReviewDraftReducer.resetPlayerCorrection(edited, 1, 1)
        val slotReset = RosterOcrReviewDraftReducer.resetSlotCorrections(playerReset, 1)
        val allReset = RosterOcrReviewDraftReducer.resetAllCorrections(edited)

        assertEquals("ocr-1-1", playerReset.slots[0].players[0].draftValue)
        assertEquals("ocr-1-2", slotReset.slots[0].players[1].draftValue)
        assertEquals("other", slotReset.slots[1].players[0].draftValue)
        assertTrue(allReset.slots.all { slot -> slot.players.all { it.draftValue == it.originalOcrValue } })
    }

    @Test
    fun missingOcrSlotStillCreatesSixBlankRowsAndRetainsSourceIssue() {
        val sourceIssue = RosterOcrValidationIssue(
            severity = RosterOcrValidationSeverity.BLOCKING,
            type = RosterOcrValidationIssueType.MISSING_TOURNAMENT_SLOT,
            tournamentSlotNumber = 12,
        )
        val draft = draft(evidence = evidence(candidates = emptyList(), issues = listOf(sourceIssue)))

        assertEquals(listOf("", "", "", "", "", ""), draft.slots[11].players.map { it.draftValue })
        assertEquals(listOf(sourceIssue), draft.slots[11].sourceIssues)
        assertTrue(draft.warningCount > 0)
    }

    @Test
    fun fourFiveAndSixPlayersAreAcceptedOptionalBlanksExcludedAndNamesTrimmed() {
        val four = filledDraft(4)
        val five = filledDraft(5)
        val six = filledDraft(6)

        assertTrue(four.canConfirm)
        assertTrue(five.canConfirm)
        assertTrue(six.canConfirm)
        assertEquals(4, four.toConfirmedRosterReplacementCandidateOrNull()!!.rosterPlayersBySlotNumber.getValue(1).size)
        assertEquals(5, five.toConfirmedRosterReplacementCandidateOrNull()!!.rosterPlayersBySlotNumber.getValue(1).size)
        assertEquals(6, six.toConfirmedRosterReplacementCandidateOrNull()!!.rosterPlayersBySlotNumber.getValue(1).size)
        assertEquals(
            listOf("player-1-1", "player-1-2", "player-1-3", "player-1-4"),
            four.toConfirmedRosterReplacementCandidateOrNull()!!.rosterPlayersBySlotNumber.getValue(1)
                .map { it.displayName },
        )
        val trimmed = RosterOcrReviewDraftReducer.updatePlayerName(four, 1, 1, "  trimmed  ")
        assertEquals("trimmed", trimmed.toConfirmedRosterReplacementCandidateOrNull()!!.rosterPlayersBySlotNumber
            .getValue(1).first().displayName)
    }

    @Test
    fun fewerThanFourAndDuplicatePlayersWithinOneTeamBlockButCrossTeamDuplicateDoesNot() {
        val tooFew = draft(evidence = evidence(candidates = emptyList()))
        val duplicate = filledDraft(4).let {
            RosterOcrReviewDraftReducer.updatePlayerName(it, 1, 2, "player-1-1")
        }
        val crossTeam = RosterOcrReviewDraftReducer.updatePlayerName(filledDraft(4), 2, 1, "player-1-1")

        assertTrue(tooFew.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.INVALID_PLAYER_COUNT })
        assertTrue(duplicate.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.DUPLICATE_PLAYER_NAME })
        assertFalse(crossTeam.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.DUPLICATE_PLAYER_NAME })
    }

    @Test
    fun missingAndDuplicateTeamsAndMalformedStructureBlock() {
        val missing = filledDraft(4).let { draft ->
            draft.copy(slots = draft.slots.map { if (it.slotNumber == 1) it.copy(currentTeamName = " ") else it })
        }.let(RosterOcrReviewDraftReducer::validate)
        val duplicate = filledDraft(4).let { draft ->
            draft.copy(slots = draft.slots.map { if (it.slotNumber == 2) it.copy(currentTeamName = "room-team-1") else it })
        }.let(RosterOcrReviewDraftReducer::validate)
        val malformed = filledDraft(4).copy(slots = filledDraft(4).slots.dropLast(1))
            .let(RosterOcrReviewDraftReducer::validate)

        assertTrue(missing.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.MISSING_TEAM_NAME })
        assertTrue(duplicate.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.DUPLICATE_TEAM_NAME })
        assertTrue(malformed.finalValidation.issues.any { it.type == RosterOcrReviewDraftIssueType.MALFORMED_STRUCTURE })
    }

    @Test
    fun sourceBlockersBecomeReviewWarningsAfterCorrectionAndDirtyStateIsDeterministic() {
        val issue = RosterOcrValidationIssue(
            severity = RosterOcrValidationSeverity.BLOCKING,
            type = RosterOcrValidationIssueType.EMPTY_PLAYER_ROW,
            tournamentSlotNumber = 1,
            playerRowIndex = 1,
        )
        val initial = draft(
            evidence = evidence(
                candidates = listOf(candidate(1, mapOf(1 to "", 2 to "a", 3 to "b", 4 to "c"))) +
                    (2..12).map { slot ->
                        candidate(slot, (1..4).associateWith { row -> "ocr-$slot-$row" })
                    },
                issues = listOf(issue),
            ),
        )
        val corrected = (1..4).fold(initial) { current, row ->
            RosterOcrReviewDraftReducer.updatePlayerName(current, 1, row, "fixed-$row")
        }

        assertTrue(initial.blockerCount > 0)
        assertFalse(initial.canConfirm)
        assertTrue(corrected.canConfirm)
        assertEquals(RosterOcrReviewDraftStatus.WARNING, corrected.status)
        assertTrue(corrected.warningCount > 0)
        assertTrue(corrected.isDirty)
    }

    @Test
    fun malformedTeamContextIsRejectedBeforeDraftCreation() {
        val result = RosterOcrReviewDraftReducer.createInitialDraft(
            tournamentId = TOURNAMENT_ID,
            currentTeamSlots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID).dropLast(1),
            evidence = evidence(),
        )

        assertTrue(result is RosterOcrReviewDraftCreationResult.Rejected)
        assertEquals(
            RosterOcrReviewDraftCreationFailure.INCOMPLETE_TEAM_CONTEXT,
            (result as RosterOcrReviewDraftCreationResult.Rejected).reason,
        )
    }

    @Test
    fun draftCreationFailureHasDistinctTypedProcessingState() {
        val state = RosterOcrReviewUiState.ReadyToProcess(
            tournamentId = TOURNAMENT_ID,
            teamSlots = TeamSlot.fixedSlotsForTournament(TOURNAMENT_ID),
            processingFailure = RosterOcrReviewProcessingFailure.DraftCreation(
                RosterOcrReviewDraftCreationFailure.DUPLICATE_TEAM_CONTEXT,
            ),
        )

        val failure = state.processingFailure as RosterOcrReviewProcessingFailure.DraftCreation
        assertEquals(RosterOcrReviewDraftCreationFailure.DUPLICATE_TEAM_CONTEXT, failure.failure)
    }

    private fun draft(
        evidence: ProcessRosterOcrEvidence = evidence(),
    ): RosterOcrReviewDraft = when (
        val result = RosterOcrReviewDraftReducer.createInitialDraft(
            tournamentId = TOURNAMENT_ID,
            currentTeamSlots = TeamSlot.SLOT_NUMBERS.map { slot ->
                TeamSlot.create(TOURNAMENT_ID, slot, "room-team-$slot")
            },
            evidence = evidence,
        )
    ) {
        is RosterOcrReviewDraftCreationResult.Created -> result.draft
        is RosterOcrReviewDraftCreationResult.Rejected -> error(result.reason)
    }

    private fun filledDraft(players: Int): RosterOcrReviewDraft {
        val base = draft(evidence = evidence(candidates = emptyList()))
        return base.slots.fold(base) { current, slot ->
            (1..players).fold(current) { updated, row ->
                RosterOcrReviewDraftReducer.updatePlayerName(
                    updated,
                    slot.slotNumber,
                    row,
                    " player-${slot.slotNumber}-$row ",
                )
            }
        }
    }

    private fun evidence(
        candidates: List<RosterTournamentSlotCandidate> = (1..12).map { slot ->
            candidate(slot, (1..4).associateWith { row -> "ocr-$slot-$row" })
        },
        issues: List<RosterOcrValidationIssue> = emptyList(),
    ): ProcessRosterOcrEvidence {
        val association = RosterSlotAssociationResult(
            tournamentSlotCandidates = candidates,
            failures = emptyList(),
        )
        return ProcessRosterOcrEvidence(
            rawExtractions = emptyList(),
            parsedCandidates = RosterCandidateParseResult(emptyList(), emptyList()),
            associatedCandidates = association,
            validation = RosterOcrValidationResult(
                status = if (issues.any { it.severity == RosterOcrValidationSeverity.BLOCKING }) {
                    RosterOcrValidationStatus.BLOCKED
                } else {
                    RosterOcrValidationStatus.READY_FOR_REVIEW
                },
                slotResults = candidates.map { candidate ->
                    RosterOcrSlotValidationResult(candidate, issues.filter {
                        it.tournamentSlotNumber == candidate.tournamentSlotNumber
                    })
                },
                globalIssues = issues.filter { issue ->
    issue.tournamentSlotNumber == null ||
        candidates.none { candidate ->
            candidate.tournamentSlotNumber == issue.tournamentSlotNumber
        }
},
            ),
        )
    }

    private fun candidate(
        slot: Int,
        values: Map<Int, String>,
    ): RosterTournamentSlotCandidate {
        val screenshot = when (slot) {
            in 1..4 -> RosterScreenshotPosition.ONE
            in 5..8 -> RosterScreenshotPosition.TWO
            else -> RosterScreenshotPosition.THREE
        }
        val visible = RosterVisibleSlotPosition.entries[(slot - 1) % 4]
        return RosterTournamentSlotCandidate(
            tournamentSlotNumber = slot,
            sourceScreenshotPosition = screenshot,
            sourceVisibleSlotPosition = visible,
            teamNameCandidate = RosterTeamNameCandidate(
                status = RosterCandidateParseStatus.UNSUPPORTED,
                failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
                rawSourceResults = emptyList(),
                confidence = RawOcrConfidence.Unavailable,
            ),
            playerNameCandidates = (1..4).map { row ->
                val value = values[row]
                val status = when {
                    value == null -> RosterCandidateParseStatus.MISSING
                    value.isEmpty() -> RosterCandidateParseStatus.EMPTY
                    value == "ambiguous" -> RosterCandidateParseStatus.AMBIGUOUS
                    else -> RosterCandidateParseStatus.PARSED
                }
                RosterPlayerNameCandidate(
                    regionIdentity = RosterRawOcrRegionIdentity(
                        screenshotPosition = screenshot,
                        visibleSlotPosition = visible,
                        regionType = RosterRawOcrRegionType.PLAYER_ROW,
                        playerRowIndex = row,
                    ),
                    playerRowIndex = row,
                    status = status,
                    candidateText = value,
                    failure = when (status) {
                        RosterCandidateParseStatus.EMPTY -> RosterCandidateParseFailure.EMPTY_TEXT
                        RosterCandidateParseStatus.AMBIGUOUS -> RosterCandidateParseFailure.MULTIPLE_FRAGMENTS
                        RosterCandidateParseStatus.MISSING -> RosterCandidateParseFailure.MISSING_EVIDENCE
                        else -> null
                    },
                    rawSourceResults = emptyList(),
                    confidence = RawOcrConfidence.Unavailable,
                )
            },
            associationStatus = RosterSlotAssociationStatus.ASSOCIATED,
        )
    }

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
    }
}
