package com.hoggamers.rankforge.domain.matching

import com.hoggamers.rankforge.domain.tournament.TeamSlot

data class ResultLobbySlotAssignmentRowResult(
    val resultPosition: Int,
    val matchResult: ResultLobbySlotMatchResult,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
    val assignmentSafety: RowTeamAssignmentSafetyResult,
    val automaticAssignedTeamSlot: Int?,
)

data class ResultLobbySlotAssignmentEvaluation(
    val rows: List<ResultLobbySlotAssignmentRowResult>,
    val assignmentSafety: TeamAssignmentSafetyResult,
)

/**
 * Pure orchestration for turning Result-to-Lobby candidate evidence into safe derived assignments.
 *
 * This component preserves the Slice 2 match result and composes the existing confidence
 * classifier and assignment safety evaluator. It never mutates OCR evidence or writes an
 * assignment back into an OCR model.
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

        val rowAssessments = orderedMatchResults.map { matchResult ->
            val confidenceAssessment = TeamMatchConfidenceTierClassifier.classify(
                matchResult.rankedCandidates,
            )
            RowEvaluationInput(
                matchResult = matchResult,
                confidenceAssessment = confidenceAssessment,
            )
        }
        val assignmentSafety = TeamAssignmentSafetyEvaluator.evaluate(
            rowAssessments.map { assessment ->
                RowTeamMatchConfidenceAssessment(
                    rowIndex = assessment.matchResult.resultPosition - RESULT_POSITION_OFFSET,
                    confidenceAssessment = assessment.confidenceAssessment,
                )
            },
        )
        val safetyByRowIndex = assignmentSafety.rowResults.associateBy { it.rowIndex }

        return ResultLobbySlotAssignmentEvaluation(
            rows = rowAssessments.map { assessment ->
                val assignmentSafetyResult = requireNotNull(
                    safetyByRowIndex[
                        assessment.matchResult.resultPosition - RESULT_POSITION_OFFSET,
                    ],
                )
                ResultLobbySlotAssignmentRowResult(
                    resultPosition = assessment.matchResult.resultPosition,
                    matchResult = assessment.matchResult,
                    confidenceAssessment = assessment.confidenceAssessment,
                    assignmentSafety = assignmentSafetyResult,
                    automaticAssignedTeamSlot = assignmentSafetyResult
                        .takeIf {
                            it.safetyStatus == TeamAssignmentSafetyStatus.SAFE_AUTOMATIC_ASSIGNMENT
                        }
                        ?.proposedTeamSlot,
                )
            },
            assignmentSafety = assignmentSafety,
        )
    }

    private data class RowEvaluationInput(
        val matchResult: ResultLobbySlotMatchResult,
        val confidenceAssessment: TeamMatchConfidenceAssessment,
    )

    private const val RESULT_POSITION_OFFSET = 1
}
