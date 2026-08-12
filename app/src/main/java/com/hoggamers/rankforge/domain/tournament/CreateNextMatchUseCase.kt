package com.hoggamers.rankforge.domain.tournament

import java.util.UUID
import kotlinx.coroutines.flow.first

sealed interface CreateNextMatchResult {
    data class Created(val match: Match) : CreateNextMatchResult

    data class Rejected(val failure: CreateNextMatchFailure) : CreateNextMatchResult
}

enum class CreateNextMatchFailure {
    TOURNAMENT_NOT_FOUND,
    NO_PARTICIPATING_TEAMS,
    INVALID_TEAM_SLOTS,
    LIMIT_REACHED,
    REPOSITORY_REJECTED,
}

class CreateNextMatchUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(tournamentId: String): CreateNextMatchResult {
        val tournament = repository.observeById(tournamentId).first()
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.TOURNAMENT_NOT_FOUND)

        val participation = repository
            .observeSlotsByTournamentId(tournamentId)
            .first()
            .analyzeTeamSlotParticipation()
        if (participation.hasGap) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.INVALID_TEAM_SLOTS)
        }
        if (participation.activeCount == 0) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.NO_PARTICIPATING_TEAMS)
        }

        val existingMatches = repository.observeMatchesByTournamentId(tournamentId).first()
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

        return when (val result = repository.createDraftMatch(match)) {
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
