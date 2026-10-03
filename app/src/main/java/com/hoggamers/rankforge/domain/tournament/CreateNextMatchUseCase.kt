package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.time.Clock
import java.time.LocalDate
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
    INVALID_GROUP_PAIRING,
    LIMIT_REACHED,
    REPOSITORY_REJECTED,
}

class CreateNextMatchUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) {
    constructor(repository: TournamentRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
        Clock.systemDefaultZone(),
    )

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(repository, authRepository, Clock.systemDefaultZone())

    suspend operator fun invoke(
        tournamentId: String,
        groupPairing: GroupPairing? = null,
    ): CreateNextMatchResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.AUTHENTICATION_REQUIRED)
        val tournament = repository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.TOURNAMENT_NOT_FOUND)

        val persistedSlots = repository
            .observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId)
            .first()
        val provisionalMatch = Match(
            id = UUID.randomUUID().toString(),
            tournamentId = tournamentId,
            matchNumber = 1,
            date = LocalDate.now(clock),
            mapName = "",
            status = MatchStatus.DRAFT,
            groupPairing = groupPairing,
        )
        val eligibleSlotNumbers = runCatching {
            MatchEligibleTeamSlotResolver().resolve(tournament, persistedSlots, provisionalMatch)
        }.getOrElse {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.INVALID_GROUP_PAIRING)
        }
        val participation = persistedSlots.analyzeTeamSlotParticipation(eligibleSlotNumbers)
        if (participation.activeCount == 0) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.NO_PARTICIPATING_TEAMS)
        }

        val existingMatches = repository.observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId).first()
        if (existingMatches.size >= MAX_MATCHES_PER_TOURNAMENT) {
            return CreateNextMatchResult.Rejected(CreateNextMatchFailure.LIMIT_REACHED)
        }

        val nextMatchNumber = nextAvailableMatchNumber(existingMatches.map { it.matchNumber })
            ?: return CreateNextMatchResult.Rejected(CreateNextMatchFailure.LIMIT_REACHED)
        val match = provisionalMatch.copy(
            tournamentId = tournamentId,
            matchNumber = nextMatchNumber,
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
    MatchCreationFailure.INVALID_GROUP_PAIRING -> CreateNextMatchFailure.INVALID_GROUP_PAIRING
    MatchCreationFailure.LIMIT_REACHED -> CreateNextMatchFailure.LIMIT_REACHED
    MatchCreationFailure.TOURNAMENT_NOT_CONFIRMED,
    MatchCreationFailure.DUPLICATE_MATCH_NUMBER,
    MatchCreationFailure.INVALID_MATCH_NUMBER,
    MatchCreationFailure.DUPLICATE_ID,
    -> CreateNextMatchFailure.REPOSITORY_REJECTED
}
