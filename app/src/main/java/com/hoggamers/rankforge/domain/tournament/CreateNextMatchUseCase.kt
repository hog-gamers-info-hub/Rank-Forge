package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.UUID
import kotlinx.coroutines.flow.first

sealed interface CreateNextMatchResult {
    data class Created(val match: Match) : CreateNextMatchResult

    data class Rejected(val failure: CreateNextMatchFailure) : CreateNextMatchResult
}

enum class CreateNextMatchFailure {
    AUTHENTICATION_REQUIRED,
    TOURNAMENT_NOT_FOUND,
    NO_PARTICIPATING_TEAMS,
    INVALID_TEAM_SLOTS,
    LIMIT_REACHED,
    REPOSITORY_REJECTED,
}

class CreateNextMatchUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(tournamentId: String): CreateNextMatchResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.AUTHENTICATION_REQUIRED)
        val tournament = repository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.TOURNAMENT_NOT_FOUND)

        val participation = repository
            .observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId)
            .first()
            .analyzeTeamSlotParticipation()
        if (participation.activeCount == 0) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.NO_PARTICIPATING_TEAMS)
        }

        val existingMatches = repository.observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId).first()
        if (existingMatches.size >= MAX_MATCHES_PER_TOURNAMENT) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.LIMIT_REACHED)
        }

        val nextMatchNumber = existingMatches.maxOfOrNull { it.matchNumber }?.plus(1) ?: 1
        val match = Match(
            id = UUID.randomUUID().toString(),
            tournamentId = tournamentId,
            matchNumber = nextMatchNumber,
            date = tournament.date,
            mapName = "",
            status = MatchStatus.DRAFT,
        )

        return when (val result = repository.createDraftMatchByOwner(match, ownerUserId)) {
            CreateMatchRepositoryResult.Created -> CreateNextMatchResult.Created(match)
            is CreateMatchRepositoryResult.Rejected -> CreateNextMatchResult.Rejected(result.toNextMatchFailure())
        }
    }
}

private fun CreateMatchRepositoryResult.Rejected.toNextMatchFailure(): CreateNextMatchFailure = when (reason) {
    MatchCreationFailure.TOURNAMENT_NOT_FOUND -> CreateNextMatchFailure.TOURNAMENT_NOT_FOUND
    MatchCreationFailure.NO_PARTICIPATING_TEAMS -> CreateNextMatchFailure.NO_PARTICIPATING_TEAMS
    MatchCreationFailure.INVALID_TEAM_SLOTS -> CreateNextMatchFailure.INVALID_TEAM_SLOTS
    MatchCreationFailure.LIMIT_REACHED -> CreateNextMatchFailure.LIMIT_REACHED
    MatchCreationFailure.TOURNAMENT_NOT_CONFIRMED,
    MatchCreationFailure.DUPLICATE_MATCH_NUMBER,
    MatchCreationFailure.DUPLICATE_ID,
    -> CreateNextMatchFailure.REPOSITORY_REJECTED
}
