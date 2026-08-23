package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
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
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(matchId: String): StartMatchCorrectionResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return StartMatchCorrectionResult.Rejected(MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED)
        val match = repository.observeMatchByIdAndOwner(matchId, ownerUserId).first()
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
    private val authRepository: AuthRepository,
    private val protectedCorrection: ProtectedMatchCorrectionAction = ProtectedMatchCorrectionAction {
        ProtectedMatchCorrectionResult.AuthenticationRequired
    },
) {
    constructor(
        repository: TournamentRepository,
        validateMatchResult: ValidateMatchResultUseCase,
        protectedCorrection: ProtectedMatchCorrectionAction = ProtectedMatchCorrectionAction {
            ProtectedMatchCorrectionResult.AuthenticationRequired
        },
    ) : this(
        repository,
        validateMatchResult,
        SetupMutationUnauthenticatedAuthRepository,
        protectedCorrection,
    )

    suspend operator fun invoke(input: SubmitMatchCorrectionInput): SubmitMatchCorrectionResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return SubmitMatchCorrectionResult.Invalid(
                validation = MatchResultValidation(),
                globalError = MatchCorrectionGlobalError.AUTHENTICATION_REQUIRED,
            )
        val match = repository.observeMatchByIdAndOwner(input.matchId, ownerUserId).first()
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

        val participantSnapshot = match.finalizedParticipantResultsOrNull()
            ?: return SubmitMatchCorrectionResult.Invalid(
                validation = validateMatchResult(match),
                globalError = MatchCorrectionGlobalError.INVALID_DATA,
            )
        val validation = validateMatchResult.validateParticipantResults(
            rows = input.rows,
            expectedTeamSlots = participantSnapshot.map { it.teamSlotNumber },
        )
        if (!validation.isValid) return SubmitMatchCorrectionResult.Invalid(validation)

        val participantResults = input.rows.map { row ->
            val placement = row.placement?.trim()?.takeIf { it.isNotBlank() }?.toInt()
            val kills = row.kills?.trim()?.takeIf { it.isNotBlank() }?.toInt() ?: 0
            MatchParticipantResult(
                teamSlotNumber = row.teamSlotNumber,
                participationStatus = row.participationStatus,
                placement = placement,
                kills = kills,
            )
        }
        val placements = participantResults.mapNotNull { result ->
            result.placement?.let { position ->
                MatchPlacement(
                    teamSlotNumber = result.teamSlotNumber,
                    position = position,
                )
            }
        }
        val kills = participantResults.map { result ->
            MatchKill(
                teamSlotNumber = result.teamSlotNumber,
                kills = result.kills,
            )
        }
        val tournament = repository.observeByIdAndOwner(match.tournamentId, ownerUserId).first()
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
                participantResults = participantResults,
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
            val result = repository.submitMatchCorrectionByOwner(
                matchId = input.matchId,
                ownerUserId = ownerUserId,
                placements = placements,
                kills = kills,
                participantResults = participantResults,
            )
        ) {
            is SubmitMatchCorrectionRepositoryResult.Submitted -> {
                when (repository.confirmCloudRevisionByOwner(match.tournamentId, ownerUserId, cloudRevision)) {
                    OwnerScopedTournamentMutationResult.Saved -> SubmitMatchCorrectionResult.Submitted(result.match)
                    OwnerScopedTournamentMutationResult.TournamentNotFound -> cloudFailure(
                        MatchCorrectionGlobalError.MATCH_NOT_FOUND,
                        match,
                    )
                }
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
