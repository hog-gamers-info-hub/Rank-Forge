package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.first

data class FinalizeMatchInput(
    val matchId: String,
    val rows: List<MatchResultRowInput>,
    val ocrEvidence: PreservedMatchOcrEvidence? = null,
)

enum class FinalizeMatchGlobalError {
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_DATA,
}

sealed interface FinalizeMatchResult {
    data class Finalized(val match: Match) : FinalizeMatchResult

    data class Invalid(
        val validation: MatchResultValidation,
        val globalError: FinalizeMatchGlobalError? = null,
    ) : FinalizeMatchResult
}

class FinalizeMatchUseCase(
    private val repository: TournamentRepository,
    private val validateMatchResult: ValidateMatchResultUseCase,
) {
    suspend operator fun invoke(input: FinalizeMatchInput): FinalizeMatchResult {
        val match = repository.observeMatchById(input.matchId).first()
            ?: return FinalizeMatchResult.Invalid(
                validation = MatchResultValidation(),
                globalError = FinalizeMatchGlobalError.MATCH_NOT_FOUND,
            )
        val participation = repository.observeSlotsByTournamentId(match.tournamentId)
            .first()
            .analyzeTeamSlotParticipation()
        if (!participation.isReadyForMatchCreation) {
            return FinalizeMatchResult.Invalid(
                validation = MatchResultValidation(),
                globalError = FinalizeMatchGlobalError.INVALID_DATA,
            )
        }
        if (match.status != MatchStatus.DRAFT) {
            return FinalizeMatchResult.Invalid(
                validation = validateMatchResult(match, participation.activeSlotNumbers),
                globalError = FinalizeMatchGlobalError.MATCH_NOT_DRAFT,
            )
        }

        if (input.rows.isEmpty()) {
            return FinalizeMatchResult.Invalid(
                validation = MatchResultValidation(),
                globalError = FinalizeMatchGlobalError.INVALID_DATA,
            )
        }

        val validation = validateMatchResult.validateForInitialFinalization(
            rows = input.rows,
            registeredTeamSlots = participation.activeSlotNumbers,
        )
        if (!validation.isValid) {
            return FinalizeMatchResult.Invalid(validation)
        }

        val rowsByTeamSlot = input.rows.associateBy { it.teamSlotNumber }
        val participantResults = participation.activeSlotNumbers
            .sorted()
            .map { teamSlotNumber ->
                rowsByTeamSlot[teamSlotNumber]?.let { row ->
                    MatchParticipantResult(
                        teamSlotNumber = teamSlotNumber,
                        participationStatus = MatchParticipationStatus.PARTICIPATED,
                        placement = row.placement!!.trim().toInt(),
                        kills = row.kills!!.trim().toInt(),
                    )
                } ?: MatchParticipantResult(
                    teamSlotNumber = teamSlotNumber,
                    participationStatus = MatchParticipationStatus.NO_SHOW,
                    placement = null,
                    kills = 0,
                )
            }

        val placements = input.rows.map { row ->
            MatchPlacement(
                teamSlotNumber = row.teamSlotNumber,
                position = row.placement!!.trim().toInt(),
            )
        }
        val kills = input.rows.map { row ->
            MatchKill(
                teamSlotNumber = row.teamSlotNumber,
                kills = row.kills!!.trim().toInt(),
            )
        }
        return when (
            val result = if (input.ocrEvidence == null) {
                repository.finalizeDraftMatch(
                    matchId = input.matchId,
                    placements = placements,
                    kills = kills,
                    participantResults = participantResults,
                )
            } else {
                repository.finalizeDraftMatchWithOcrEvidence(
                    matchId = input.matchId,
                    placements = placements,
                    kills = kills,
                    participantResults = participantResults,
                    evidence = input.ocrEvidence,
                )
            }
        ) {
            is FinalizeMatchRepositoryResult.Finalized -> FinalizeMatchResult.Finalized(result.match)
            is FinalizeMatchRepositoryResult.Rejected -> FinalizeMatchResult.Invalid(
                validation = validateMatchResult(match),
                globalError = when (result.reason) {
                    FinalizeMatchFailure.MATCH_NOT_FOUND -> FinalizeMatchGlobalError.MATCH_NOT_FOUND
                    FinalizeMatchFailure.MATCH_NOT_DRAFT -> FinalizeMatchGlobalError.MATCH_NOT_DRAFT
                    FinalizeMatchFailure.INVALID_DATA -> FinalizeMatchGlobalError.INVALID_DATA
                },
            )
        }
    }
}

enum class FinalizeMatchFailure {
    MATCH_NOT_FOUND,
    MATCH_NOT_DRAFT,
    INVALID_DATA,
}

sealed interface FinalizeMatchRepositoryResult {
    data class Finalized(val match: Match) : FinalizeMatchRepositoryResult

    data class Rejected(val reason: FinalizeMatchFailure) : FinalizeMatchRepositoryResult
}
