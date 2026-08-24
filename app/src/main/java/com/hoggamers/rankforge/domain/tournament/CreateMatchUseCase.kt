package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first

data class CreateMatchInput(
    val tournamentId: String,
    val matchNumber: String,
    val date: LocalDate?,
    val mapName: String,
)

enum class MatchField {
    TOURNAMENT,
    MATCH_NUMBER,
    DATE,
    MAP,
}

enum class MatchValidationError {
    REQUIRED,
    INVALID,
    DUPLICATE,
    TOURNAMENT_NOT_FOUND,
    TOURNAMENT_NOT_CONFIRMED,
    NO_PARTICIPATING_TEAMS,
    INVALID_TEAM_SLOTS,
    LIMIT_REACHED,
}

sealed interface CreateMatchResult {
    data class Created(val match: Match) : CreateMatchResult

    data object AuthenticationRequired : CreateMatchResult

    data class Invalid(
        val errors: Map<MatchField, MatchValidationError>,
    ) : CreateMatchResult
}

class CreateMatchUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(input: CreateMatchInput): CreateMatchResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return CreateMatchResult.AuthenticationRequired
        val tournament = repository.observeByIdAndOwner(input.tournamentId, ownerUserId).first()
        if (tournament == null) {
            return CreateMatchResult.Invalid(
                mapOf(MatchField.TOURNAMENT to MatchValidationError.TOURNAMENT_NOT_FOUND),
            )
        }

        val participation = repository
            .observeSlotsByTournamentIdAndOwner(input.tournamentId, ownerUserId)
            .first()
            .analyzeTeamSlotParticipation()
        if (participation.activeCount == 0) {
            return CreateMatchResult.Invalid(
                mapOf(MatchField.TOURNAMENT to MatchValidationError.NO_PARTICIPATING_TEAMS),
            )
        }

        val errors = buildMap {
            val trimmedMatchNumber = input.matchNumber.trim()
            val parsedMatchNumber = trimmedMatchNumber.toIntOrNull()
            when {
                input.matchNumber.isBlank() -> put(MatchField.MATCH_NUMBER, MatchValidationError.REQUIRED)
                trimmedMatchNumber.any { it !in '0'..'9' } || parsedMatchNumber == null || parsedMatchNumber <= 0 -> {
                    put(MatchField.MATCH_NUMBER, MatchValidationError.INVALID)
                }
            }
            if (input.date == null) {
                put(MatchField.DATE, MatchValidationError.REQUIRED)
            }
            if (input.mapName.isBlank()) {
                put(MatchField.MAP, MatchValidationError.REQUIRED)
            }
        }
        if (errors.isNotEmpty()) {
            return CreateMatchResult.Invalid(errors)
        }

        val match = Match(
            id = UUID.randomUUID().toString(),
            tournamentId = input.tournamentId,
            matchNumber = input.matchNumber.trim().toInt(),
            date = input.date!!,
            mapName = input.mapName.trim(),
            status = MatchStatus.DRAFT,
        )
        return when (val result = repository.createDraftMatchByOwner(match, ownerUserId)) {
            CreateMatchRepositoryResult.Created -> CreateMatchResult.Created(match)
            is CreateMatchRepositoryResult.Rejected -> CreateMatchResult.Invalid(
                mapOf(result.reason.field to result.reason.error),
            )
        }
    }
}

enum class MatchCreationFailure(val field: MatchField, val error: MatchValidationError) {
    TOURNAMENT_NOT_FOUND(MatchField.TOURNAMENT, MatchValidationError.TOURNAMENT_NOT_FOUND),
    TOURNAMENT_NOT_CONFIRMED(MatchField.TOURNAMENT, MatchValidationError.TOURNAMENT_NOT_CONFIRMED),
    NO_PARTICIPATING_TEAMS(MatchField.TOURNAMENT, MatchValidationError.NO_PARTICIPATING_TEAMS),
    INVALID_TEAM_SLOTS(MatchField.TOURNAMENT, MatchValidationError.INVALID_TEAM_SLOTS),
    DUPLICATE_MATCH_NUMBER(MatchField.MATCH_NUMBER, MatchValidationError.DUPLICATE),
    LIMIT_REACHED(MatchField.TOURNAMENT, MatchValidationError.LIMIT_REACHED),
    DUPLICATE_ID(MatchField.TOURNAMENT, MatchValidationError.INVALID),
}

sealed interface CreateMatchRepositoryResult {
    data object Created : CreateMatchRepositoryResult

    data class Rejected(val reason: MatchCreationFailure) : CreateMatchRepositoryResult
}
