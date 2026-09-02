package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class ResultLobbySlotAssignmentRowResult(
    val resultPosition: Int,
    val matchResult: ResultLobbySlotMatchResult,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
    val assignmentSafety: RowTeamAssignmentSafetyResult,
    val automaticAssignedTeamSlot: Int?,
    val proposedTeamSlot: Int?,
    val winningVotePercent: Int?,
    val decisionStatus: ResultLobbySlotDecisionStatus,
    val decisionReason: ResultLobbySlotDecisionReason,
)

data class ResultLobbySlotAssignmentEvaluation(
    val rows: List<ResultLobbySlotAssignmentRowResult>,
    val assignmentSafety: TeamAssignmentSafetyResult,
)

/**
 * Pure orchestration for turning Result-to-Lobby candidate evidence into safe derived assignments.
 *
 * This component aggregates independent Result-row votes globally. The legacy confidence
 * assessment is retained only for the existing review presentation contract; it does not
 * decide the Result-Lobby assignment.
 */
object ResultLobbySlotAssignmentEvaluator {
    fun evaluate(
        matchResults: List<ResultLobbySlotMatchResult>,
    ): ResultLobbySlotAssignmentEvaluation {
        require(matchResults.map { it.resultPosition }.distinct().size == matchResults.size) {
            "Result positions must be unique."
        }

        val orderedMatchResults = matchResults
            .onEach { matchResult ->
                require(matchResult.resultPosition in TeamSlot.SLOT_NUMBERS) {
                    "Result position must be between 1 and 12."
                }
            }
            .sortedBy { it.resultPosition }

        val duplicateConflictPositions = orderedMatchResults
            .mapNotNull { matchResult ->
                matchResult.automaticAssignedTeamSlot?.let { teamSlot ->
                    teamSlot to matchResult
                }
            }
            .groupBy(
                keySelector = { (teamSlot, _) -> teamSlot },
                valueTransform = { (_, matchResult) -> matchResult },
            )
            .values
            .filter { claimants -> claimants.size > 1 }
            .flatMap { claimants ->
                val highestVotePercent = claimants.maxOf { it.winningVotePercent ?: Int.MIN_VALUE }
                val topClaimants = claimants.filter {
                    (it.winningVotePercent ?: Int.MIN_VALUE) == highestVotePercent
                }
                if (topClaimants.size == 1) {
                    claimants - topClaimants.single()
                } else {
                    claimants
                }
            }
            .mapTo(mutableSetOf()) { it.resultPosition }

        val rowResults = orderedMatchResults.map { matchResult ->
            val confidenceAssessment = TeamMatchConfidenceTierClassifier.classify(
                matchResult.rankedCandidates,
            )
            val hasDuplicateSlot = matchResult.resultPosition in duplicateConflictPositions
            val decisionReason = if (hasDuplicateSlot) {
                ResultLobbySlotDecisionReason.DUPLICATE_SLOT_ACROSS_RESULT_ROWS
            } else {
                matchResult.decisionReason
            }
            val automaticAssignedTeamSlot = matchResult.automaticAssignedTeamSlot
                ?.takeUnless { hasDuplicateSlot }
            val assignmentSafety = compatibilitySafetyResult(
                matchResult = matchResult,
                confidenceAssessment = confidenceAssessment,
                hasDuplicateSlot = hasDuplicateSlot,
            )
            ResultLobbySlotAssignmentRowResult(
                resultPosition = matchResult.resultPosition,
                matchResult = matchResult,
                confidenceAssessment = confidenceAssessment,
                assignmentSafety = assignmentSafety,
                automaticAssignedTeamSlot = automaticAssignedTeamSlot,
                proposedTeamSlot = matchResult.proposedTeamSlot,
                winningVotePercent = matchResult.winningVotePercent,
                decisionStatus = if (hasDuplicateSlot) {
                    ResultLobbySlotDecisionStatus.MANUAL
                } else {
                    matchResult.decisionStatus
                },
                decisionReason = decisionReason,
            )
        }

        return ResultLobbySlotAssignmentEvaluation(
            rows = rowResults,
            assignmentSafety = TeamAssignmentSafetyResult(
                rowCount = rowResults.size,
                safeAssignmentCount = rowResults.count {
                    it.assignmentSafety.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
                },
                rowResults = rowResults.map { it.assignmentSafety },
            ),
        )
    }

    private fun compatibilitySafetyResult(
        matchResult: ResultLobbySlotMatchResult,
        confidenceAssessment: TeamMatchConfidenceAssessment,
        hasDuplicateSlot: Boolean,
    ): RowTeamAssignmentSafetyResult {
        val safetyStatus = when {
            hasDuplicateSlot -> TeamAssignmentSafetyStatus.REVIEW_REQUIRED
            matchResult.decisionStatus == ResultLobbySlotDecisionStatus.AUTOMATIC ->
                TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
            matchResult.decisionReason == ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH ->
                TeamAssignmentSafetyStatus.MANUAL_REQUIRED
            else -> TeamAssignmentSafetyStatus.REVIEW_REQUIRED
        }
        val reasons = when {
            hasDuplicateSlot -> setOf(TeamAssignmentSafetyReason.DUPLICATE_TEAM_CANDIDATE)
            matchResult.decisionReason == ResultLobbySlotDecisionReason.NO_PLAUSIBLE_MATCH -> setOf(
                TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER,
                TeamAssignmentSafetyReason.NO_SUGGESTION,
            )
            matchResult.decisionStatus == ResultLobbySlotDecisionStatus.AUTOMATIC -> emptySet()
            else -> setOf(TeamAssignmentSafetyReason.NOT_AUTOMATIC_TIER)
        }
        return RowTeamAssignmentSafetyResult(
            rowIndex = matchResult.resultPosition - RESULT_POSITION_OFFSET,
            confidenceAssessment = confidenceAssessment,
            safetyStatus = safetyStatus,
            proposedTeamSlot = matchResult.proposedTeamSlot,
            reasons = reasons,
        )
    }

    private const val RESULT_POSITION_OFFSET = 1
}
