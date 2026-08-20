package com.hoggamers.rankforge.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOcrReviewTeamSlotAssistantTest {
    @Test
    fun tenValidAssignmentsLeaveExactlyTwoRemainingSlots() {
        val state = derive((1..10).map { it.toString() } + listOf("", ""))

        assertEquals((1..10).toSet(), state.claimedTeamSlots)
        assertEquals(listOf(11, 12), state.remainingTeamSlots)
        assertEquals(listOf(10, 11), state.unresolvedRowIndexes)
    }

    @Test
    fun assigningOneRemainingSlotRecomputesTheSingleRemainingSlot() {
        val initial = draft((1..10).map { it.toString() } + listOf("", ""))
        val updated = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(
            draft = initial,
            rowIndex = 10,
            value = "11",
        )

        val state = MatchOcrReviewTeamSlotAssistant.derive(updated)

        assertEquals(listOf(12), state.remainingTeamSlots)
        assertEquals((1..11).toSet(), state.claimedTeamSlots)
    }

    @Test
    fun ownCurrentValidSlotRemainsAvailableWhileEditingThatRow() {
        val state = derive(
            slots = listOf("11", "1", "2", "3", "5", "6", "7", "8", "9", "10", "", ""),
            manualRowIndexes = setOf(0),
        )

        assertEquals(listOf(4, 11, 12), state.availableOptionsByRow.getValue(0).map { it.teamSlot })
    }

    @Test
    fun anotherRowsClaimIsUnavailableToAnUnresolvedRow() {
        val state = derive(listOf("", "4") + List(10) { "" })

        assertFalse(4 in state.availableOptionsByRow.getValue(0).map { it.teamSlot })
    }

    @Test
    fun invalidValuesDoNotClaimTeamSlots() {
        val state = derive(listOf("13", "not-a-slot") + List(10) { "" })

        assertTrue(state.claimedTeamSlots.isEmpty())
        assertEquals((1..12).toList(), state.remainingTeamSlots)
    }

    @Test
    fun duplicatesRemainVisibleAsUnresolvedWithoutUnsafeDeduction() {
        val state = derive(listOf("4", "4", "") + List(9) { "" })

        assertEquals(setOf(4), state.claimedTeamSlots)
        assertFalse(4 in state.remainingTeamSlots)
        assertTrue(0 in state.unresolvedRowIndexes)
        assertTrue(1 in state.unresolvedRowIndexes)
        assertTrue(2 in state.unresolvedRowIndexes)
        assertFalse(4 in state.availableOptionsByRow.getValue(2).map { it.teamSlot })
    }

    @Test
    fun evidenceRanksByVoteThenSimilarityThenSlot() {
        val state = derive(
            slots = List(12) { "" },
            evidence = listOf(
                candidate(slot = 4, vote = 25, similarity = 70),
                candidate(slot = 11, vote = 50, similarity = 60),
                candidate(slot = 7, vote = 50, similarity = 80),
                candidate(slot = 9, vote = 50, similarity = 80),
            ),
        )

        assertEquals(
            listOf(7, 9, 11, 4),
            state.availableOptionsByRow.getValue(0).take(4).map { it.teamSlot },
        )
    }

    @Test
    fun slotsWithoutEvidenceRemainSelectableAfterEvidenceOptions() {
        val state = derive(
            slots = List(12) { "" },
            evidence = listOf(candidate(slot = 4, vote = 100, similarity = 90)),
        )

        val options = state.availableOptionsByRow.getValue(0).map { it.teamSlot }
        assertEquals(12, options.size)
        assertEquals(4, options.first())
        assertTrue(12 in options)
    }

    @Test
    fun manualProposedSlotDoesNotReserveUntilItIsInCorrectionDraft() {
        val state = derive(
            slots = List(12) { "" },
            manualRowIndexes = setOf(0),
            evidence = listOf(candidate(slot = 12, vote = 50, similarity = 70)),
        )

        assertTrue(12 in state.remainingTeamSlots)
        assertTrue(12 in state.availableOptionsByRow.getValue(0).map { it.teamSlot })
    }

    private fun derive(
        slots: List<String>,
        manualRowIndexes: Set<Int> = emptySet(),
        evidence: List<MatchOcrReviewTeamSlotCandidateUiState> = emptyList(),
    ): MatchOcrReviewTeamSlotAssistantState = MatchOcrReviewTeamSlotAssistant.derive(
        correctionDraft = draft(slots),
        manualRowIndexes = manualRowIndexes,
        evidenceByRow = mapOf(0 to evidence),
    )

    private fun draft(slots: List<String>): MatchOcrReviewCorrectionDraft {
        require(slots.size == 12)
        return MatchOcrReviewCorrectionDraftReducer.validate(
            MatchOcrReviewCorrectionDraft(
                rows = slots.mapIndexed { index, slot ->
                    MatchOcrReviewRowCorrectionDraft(
                        rowIndex = index,
                        originalPlacementValue = (index + 1).toString(),
                        originalKillsValue = "0",
                        originalAssignedTeamSlotValue = slot,
                        placementDraftValue = (index + 1).toString(),
                        killsDraftValue = "0",
                        assignedTeamSlotDraftValue = slot,
                        originallyRequiredManualReview = false,
                        weakConfidenceOrSafetyEvidence = false,
                        validation = MatchOcrReviewRowCorrectionValidation(),
                    )
                },
            ),
        )
    }

    private fun candidate(
        slot: Int,
        vote: Int,
        similarity: Int,
    ): MatchOcrReviewTeamSlotCandidateUiState = MatchOcrReviewTeamSlotCandidateUiState(
        teamSlot = slot,
        votePercent = vote,
        bestSimilarityScore = similarity,
    )
}
