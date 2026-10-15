package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first

data class GroupRotationMatchCreationInput(
    val tournamentId: String,
    val pairing: GroupPairing,
    val date: LocalDate,
    val mapName: String,
)

enum class GroupRotationMatchCreationFailure {
    AUTHENTICATION_REQUIRED,
    TOURNAMENT_NOT_FOUND,
    INVALID_PAIRING,
    IDENTITY_NOT_READY,
    INVALID_IDENTITY,
    EMPTY_PAIRING,
    LIMIT_REACHED,
    REPOSITORY_REJECTED,
}

sealed interface GroupRotationMatchCreationResult {
    data class Created(
        val match: Match,
        val identitySnapshot: GroupRotationMatchIdentitySnapshot,
    ) : GroupRotationMatchCreationResult

    data class Rejected(
        val failure: GroupRotationMatchCreationFailure,
    ) : GroupRotationMatchCreationResult
}

enum class GroupRotationMatchCreationRepositoryFailure {
    TOURNAMENT_NOT_FOUND,
    INVALID_PAIRING,
    INVALID_IDENTITY,
    EMPTY_PAIRING,
    DUPLICATE_MATCH_ID,
    DUPLICATE_MATCH_NUMBER,
    INVALID_MATCH_NUMBER,
    LIMIT_REACHED,
}

sealed interface GroupRotationMatchCreationRepositoryResult {
    data class Created(
        val identitySnapshot: GroupRotationMatchIdentitySnapshot,
    ) : GroupRotationMatchCreationRepositoryResult

    data class Rejected(
        val failure: GroupRotationMatchCreationRepositoryFailure,
    ) : GroupRotationMatchCreationRepositoryResult
}

class CreateGroupRotationMatchUseCase(
    private val repository: TournamentRepository,
    private val identityRepository: GroupRotationTeamIdentityLocalRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) {
    constructor(
        repository: TournamentRepository,
        identityRepository: GroupRotationTeamIdentityLocalRepository,
        authRepository: AuthRepository,
    ) : this(repository, identityRepository, authRepository, Clock.systemDefaultZone())

    suspend operator fun invoke(
        input: GroupRotationMatchCreationInput,
    ): GroupRotationMatchCreationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.AUTHENTICATION_REQUIRED,
            )
        val tournament = repository.observeByIdAndOwner(input.tournamentId, ownerUserId).first()
            ?: return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.TOURNAMENT_NOT_FOUND,
            )
        if (tournament.format != TournamentFormat.GROUP_ROTATION ||
            input.pairing !in tournament.selectedGroupPairings
        ) {
            return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.INVALID_PAIRING,
            )
        }
        val saved = when (val result = identityRepository.read(tournament)) {
            is GroupRotationTeamIdentityReadResult.Loaded -> result.snapshot
            is GroupRotationTeamIdentityReadResult.Invalid,
            GroupRotationTeamIdentityReadResult.Unsupported,
            -> return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.IDENTITY_NOT_READY,
            )
        }
        val snapshot = when (
            val result = GroupRotationMatchIdentitySnapshotResolver().resolve(
                tournament = tournament,
                pairing = input.pairing,
                saved = saved,
            )
        ) {
            is GroupRotationMatchIdentitySnapshotResult.Loaded -> result.snapshot
            is GroupRotationMatchIdentitySnapshotResult.Invalid ->
                return GroupRotationMatchCreationResult.Rejected(
                    GroupRotationMatchCreationFailure.INVALID_IDENTITY,
                )
        }
        if (snapshot.teams.isEmpty()) {
            return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.EMPTY_PAIRING,
            )
        }
        val existingMatches = repository
            .observeMatchesByTournamentIdAndOwner(input.tournamentId, ownerUserId)
            .first()
        val matchNumber = nextAvailableMatchNumber(existingMatches.map { it.matchNumber })
            ?: return GroupRotationMatchCreationResult.Rejected(
                GroupRotationMatchCreationFailure.LIMIT_REACHED,
            )
        val match = Match(
            id = UUID.randomUUID().toString(),
            tournamentId = input.tournamentId,
            matchNumber = matchNumber,
            date = input.date,
            mapName = input.mapName.trim(),
            status = MatchStatus.DRAFT,
            groupPairing = input.pairing,
        )
        return when (
            val result = repository.createGroupRotationMatchByOwner(
                match = match,
                snapshot = snapshot,
                ownerUserId = ownerUserId,
            )
        ) {
            is GroupRotationMatchCreationRepositoryResult.Created ->
                GroupRotationMatchCreationResult.Created(match, result.identitySnapshot)
            is GroupRotationMatchCreationRepositoryResult.Rejected ->
                GroupRotationMatchCreationResult.Rejected(result.failure.toCreationFailure())
        }
    }
}

private fun GroupRotationMatchCreationRepositoryFailure.toCreationFailure() = when (this) {
    GroupRotationMatchCreationRepositoryFailure.TOURNAMENT_NOT_FOUND ->
        GroupRotationMatchCreationFailure.TOURNAMENT_NOT_FOUND
    GroupRotationMatchCreationRepositoryFailure.INVALID_PAIRING ->
        GroupRotationMatchCreationFailure.INVALID_PAIRING
    GroupRotationMatchCreationRepositoryFailure.INVALID_IDENTITY ->
        GroupRotationMatchCreationFailure.INVALID_IDENTITY
    GroupRotationMatchCreationRepositoryFailure.EMPTY_PAIRING ->
        GroupRotationMatchCreationFailure.EMPTY_PAIRING
    GroupRotationMatchCreationRepositoryFailure.DUPLICATE_MATCH_ID,
    GroupRotationMatchCreationRepositoryFailure.DUPLICATE_MATCH_NUMBER,
    GroupRotationMatchCreationRepositoryFailure.INVALID_MATCH_NUMBER,
    -> GroupRotationMatchCreationFailure.REPOSITORY_REJECTED
    GroupRotationMatchCreationRepositoryFailure.LIMIT_REACHED ->
        GroupRotationMatchCreationFailure.LIMIT_REACHED
}
