package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.first

enum class MatchCorrectionGlobalError {
    MATCH_NOT_FOUND,
    MATCH_NOT_FINALIZED,
    INVALID_DATA,
    AUTHENTICATION_REQUIRED,
    AUTHORIZATION_FAILURE,
    NETWORK_FAILURE,
    CONFLICT,
    MISSING_REVISION,
}

sealed interface StartMatchCorrectionResult {
    data class Started(val match: Match) : StartMatchCorrectionResult

    data class Rejected(val error: MatchCorrectionGlobalError) : StartMatchCorrectionResult
}

class StartMatchCorrectionUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(matchId: String): StartMatchCorrectionResult {
        val match = repository.observeMatchById(matchId).first()
            ?: return StartMatchCorrectionResult.Rejected(MatchCorrectionGlobalError.MATCH_NOT_FOUND)
        return if (match.status == MatchStatus.FINALIZED) {
            StartMatchCorrectionResult.Started(match)
        } else {
            StartMatchCorrectionResult.Rejected(MatchCorrectionGlobalError.MATCH_NOT_FINALIZED)
        }
    }
}

data class SubmitMatchCorrectionInput(
    val matchId: String,
    val rows: List<MatchResultRowInput>,
)

sealed interface SubmitMatchCorrectionResult {
    data class Submitted(val match: Match) : SubmitMatchCorrectionResult

    data class Invalid(
        val validation: MatchResultValidation,
        val globalError: MatchCorrectionGlobalError? = null,
    ) : SubmitMatchCorrectionResult
}

class SubmitMatchCorrectionUseCase(
    private val repository: TournamentRepository,
    private val validateMatchResult: ValidateMatchResultUseCase,
    private val protectedCorrection: ProtectedMatchCorrectionAction = ProtectedMatchCorrectionAction {
        ProtectedMatchCorrectionResult.AuthenticationRequired
    },
) {
    suspend operator fun invoke(input: SubmitMatchCorrectionInput): SubmitMatchCorrectionResult {
        val match = repository.observeMatchById(input.matchId).first()
            ?: return SubmitMatchCorrectionResult.Invalid(
                validation = MatchResultValidation(),
                globalError = MatchCorrectionGlobalError.MATCH_NOT_FOUND,
            )
        if (match.status != MatchStatus.FINALIZED) {
            return SubmitMatchCorrectionResult.Invalid(
                validation = validateMatchResult(match),
                globalError = MatchCorrectionGlobalError.MATCH_NOT_FINALIZED,
            )
        }

        val validation = validateMatchResult(input.rows)
        if (!validation.isValid) return SubmitMatchCorrectionResult.Invalid(validation)

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
        val tournament = repository.observeById(match.tournamentId).first()
            ?: return cloudFailure(MatchCorrectionGlobalError.MATCH_NOT_FOUND, match)
        val expectedRevision = repository.readLocalRevisionState(match.tournamentId).expectedCloudRevision
            ?: return cloudFailure(MatchCorrectionGlobalError.MISSING_REVISION, match)
        val protectedResult = protectedCorrection(
            ProtectedMatchCorrectionRequest(
                tournament = tournament,
                match = match,
                placements = placements,
                kills = kills,
                expectedRevision = expectedRevision,
            ),
        )
        val cloudRevision = when (protectedResult) {
            is ProtectedMatchCorrectionResult.Success -> protectedResult.revision
            is ProtectedMatchCorrectionResult.AlreadyCorrected -> protectedResult.revision
            ProtectedMatchCorrectionResult.AuthenticationRequired -> return cloudFailure(MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED, match)
            ProtectedMatchCorrectionResult.AuthorizationFailure -> return cloudFailure(MatchCorrectionGlobalError.AUTHORIZATION_FAILURE, match)
            ProtectedMatchCorrectionResult.NetworkFailure -> return cloudFailure(MatchCorrectionGlobalError.NETWORK_FAILURE, match)
            ProtectedMatchCorrectionResult.ValidationFailure -> return cloudFailure(MatchCorrectionGlobalError.INVALID_DATA, match)
            ProtectedMatchCorrectionResult.MatchNotFinalized -> return cloudFailure(MatchCorrectionGlobalError.MATCH_NOT_FINALIZED, match)
            is ProtectedMatchCorrectionResult.Conflict -> return cloudFailure(
                if (protectedResult.conflict is com.hoggamers.rankforge.domain.sync.RevisionConflict.MissingRevision) {
                    MatchCorrectionGlobalError.MISSING_REVISION
                } else {
                    MatchCorrectionGlobalError.CONFLICT
                },
                match,
            )
        }
        return when (
            val result = repository.submitMatchCorrection(
                matchId = input.matchId,
                placements = placements,
                kills = kills,
            )
        ) {
            is SubmitMatchCorrectionRepositoryResult.Submitted -> {
                repository.confirmCloudRevision(match.tournamentId, cloudRevision)
                SubmitMatchCorrectionResult.Submitted(result.match)
            }
            is SubmitMatchCorrectionRepositoryResult.Rejected ->
                SubmitMatchCorrectionResult.Invalid(
                    validation = validateMatchResult(match),
                    globalError = when (result.reason) {
                        MatchCorrectionFailure.MATCH_NOT_FOUND -> MatchCorrectionGlobalError.MATCH_NOT_FOUND
                        MatchCorrectionFailure.MATCH_NOT_FINALIZED -> MatchCorrectionGlobalError.MATCH_NOT_FINALIZED
                        MatchCorrectionFailure.INVALID_DATA -> MatchCorrectionGlobalError.INVALID_DATA
                    },
                )
        }
    }

    private fun cloudFailure(
        error: MatchCorrectionGlobalError,
        match: Match,
    ): SubmitMatchCorrectionResult.Invalid = SubmitMatchCorrectionResult.Invalid(
        validation = validateMatchResult(match),
        globalError = error,
    )
}

enum class MatchCorrectionFailure {
    MATCH_NOT_FOUND,
    MATCH_NOT_FINALIZED,
    INVALID_DATA,
}

sealed interface SubmitMatchCorrectionRepositoryResult {
    data class Submitted(val match: Match) : SubmitMatchCorrectionRepositoryResult

    data class Rejected(val reason: MatchCorrectionFailure) : SubmitMatchCorrectionRepositoryResult
}
