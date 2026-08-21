package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class MatchOcrReviewTeamSlotAssistantState(
    val claimedTeamSlots: Set<Int>,
    val remainingTeamSlots: List<Int>,
    val unresolvedRowIndexes: List<Int>,
    val availableOptionsByRow: Map<Int, List<MatchOcrReviewTeamSlotCandidateUiState>>,
)

object MatchOcrReviewTeamSlotAssistant {
    private val teamSlotBlockers = setOf(
        MatchOcrReviewCorrectionReason.MISSING_TEAM_SLOT,
        MatchOcrReviewCorrectionReason.INVALID_TEAM_SLOT,
        MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT,
    )

    fun derive(
        correctionDraft: MatchOcrReviewCorrectionDraft,
        manualRowIndexes: Set<Int> = emptySet(),
        evidenceByRow: Map<Int, List<MatchOcrReviewTeamSlotCandidateUiState>> = emptyMap(),
    ): MatchOcrReviewTeamSlotAssistantState {
        val claimedTeamSlots = correctionDraft.rows
            .filterNot { it.isExcluded }
            .mapNotNull { it.assignedTeamSlotDraftValue.validTeamSlotOrNull() }
            .toSet()
        val remainingTeamSlots = TeamSlot.SLOT_NUMBERS
            .filterNot { it in claimedTeamSlots }
        val unresolvedRows = correctionDraft.rows
            .filter { row ->
                !row.isExcluded &&
                    (
                        row.rowIndex in manualRowIndexes ||
                            row.validation.blockers.any { it in teamSlotBlockers }
                        )
            }
            .sortedBy { it.rowIndex }

        val availableOptionsByRow = unresolvedRows.associate { row ->
            val ownSlot = row.assignedTeamSlotDraftValue.validTeamSlotOrNull()
            val availableSlots = (remainingTeamSlots + ownSlot)
                .filterNotNull()
                .distinct()
                .sorted()
            val candidatesBySlot = evidenceByRow[row.rowIndex].orEmpty()
                .filter { it.teamSlot in availableSlots && it.teamSlot in TeamSlot.SLOT_NUMBERS }
                .groupBy { it.teamSlot }
                .mapValues { (_, candidates) -> candidates.maxWithOrNull(candidateComparator)!! }
            val rankedEvidence = candidatesBySlot.values.sortedWith(candidateComparator)
            val remainingWithoutEvidence = availableSlots
                .filterNot { it in candidatesBySlot }
                .map { slot ->
                    MatchOcrReviewTeamSlotCandidateUiState(
                        teamSlot = slot,
                        votePercent = 0,
                        bestSimilarityScore = null,
                    )
                }
            row.rowIndex to rankedEvidence + remainingWithoutEvidence
        }

        return MatchOcrReviewTeamSlotAssistantState(
            claimedTeamSlots = claimedTeamSlots,
            remainingTeamSlots = remainingTeamSlots,
            unresolvedRowIndexes = unresolvedRows.map { it.rowIndex },
            availableOptionsByRow = availableOptionsByRow,
        )
    }

    fun deriveForUiState(
        uiState: MatchOcrReviewUiState.Ready,
    ): MatchOcrReviewTeamSlotAssistantState? {
        val correctionDraft = uiState.correctionDraft ?: return null
        return derive(
            correctionDraft = correctionDraft,
            manualRowIndexes = uiState.rows
                .filter { it.originalSuggestedTeamSlot == null }
                .map { it.rowIndex }
                .toSet(),
            evidenceByRow = uiState.rows.associate { it.rowIndex to it.resultLobbyTeamSlotCandidates },
        )
    }

    private val candidateComparator = compareByDescending<MatchOcrReviewTeamSlotCandidateUiState> {
        it.votePercent
    }.thenByDescending {
        it.bestSimilarityScore ?: Int.MIN_VALUE
    }.thenBy {
        it.teamSlot
    }

    private fun String.validTeamSlotOrNull(): Int? = trim()
        .takeIf { it.isNotEmpty() && it.all { character -> character in '0'..'9' } }
        ?.toIntOrNull()
        ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
}
