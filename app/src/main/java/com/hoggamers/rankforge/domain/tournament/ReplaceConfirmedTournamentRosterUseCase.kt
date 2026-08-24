package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class ConfirmedRosterReplacementCandidate(
    val tournamentId: String,
    val teamNamesBySlotNumber: Map<Int, String>,
    val rosterPlayersBySlotNumber: Map<Int, List<RosterPlayer>>,
)

sealed interface ReplaceConfirmedTournamentRosterRepositoryResult {
    data object Replaced : ReplaceConfirmedTournamentRosterRepositoryResult

    data object TournamentNotFound : ReplaceConfirmedTournamentRosterRepositoryResult

    data object InvalidCandidate : ReplaceConfirmedTournamentRosterRepositoryResult

    data object BlockedByExistingMatches : ReplaceConfirmedTournamentRosterRepositoryResult
}

sealed interface ReplaceConfirmedTournamentRosterResult {
    data object Replaced : ReplaceConfirmedTournamentRosterResult

    data object TournamentNotFound : ReplaceConfirmedTournamentRosterResult

    data object InvalidCandidate : ReplaceConfirmedTournamentRosterResult

    data object BlockedByExistingMatches : ReplaceConfirmedTournamentRosterResult

    data object AuthenticationRequired : ReplaceConfirmedTournamentRosterResult
}

class ReplaceConfirmedTournamentRosterUseCase(
    private val repository: TournamentRepository,
    private val rosterValidator: RosterValidator,
    private val authRepository: AuthRepository,
) {
    constructor(
        repository: TournamentRepository,
        rosterValidator: RosterValidator,
    ) : this(repository, rosterValidator, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(
        candidate: ConfirmedRosterReplacementCandidate,
    ): ReplaceConfirmedTournamentRosterResult {
        if (!candidate.isStructurallyComplete()) {
            return ReplaceConfirmedTournamentRosterResult.InvalidCandidate
        }

        val validation = rosterValidator.validate(
            TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                RosterValidationTeam(
                    slotNumber = slotNumber,
                    teamName = candidate.teamNamesBySlotNumber.getValue(slotNumber),
                    players = candidate.rosterPlayersBySlotNumber.getValue(slotNumber)
                        .mapIndexed { playerIndex, player ->
                            RosterValidationPlayer(
                                playerIndex = playerIndex,
                                displayName = player.displayName,
                            )
                        },
                )
            },
        )
        if (validation.issues.isNotEmpty()) {
            return ReplaceConfirmedTournamentRosterResult.InvalidCandidate
        }

        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return ReplaceConfirmedTournamentRosterResult.AuthenticationRequired
        return mapRepositoryResult(
            repository.replaceConfirmedTournamentRosterByOwner(candidate, ownerUserId),
        )
    }

    private fun mapRepositoryResult(
        repositoryResult: ReplaceConfirmedTournamentRosterRepositoryResult,
    ): ReplaceConfirmedTournamentRosterResult = when (repositoryResult) {
            ReplaceConfirmedTournamentRosterRepositoryResult.Replaced ->
                ReplaceConfirmedTournamentRosterResult.Replaced
            ReplaceConfirmedTournamentRosterRepositoryResult.TournamentNotFound ->
                ReplaceConfirmedTournamentRosterResult.TournamentNotFound
            ReplaceConfirmedTournamentRosterRepositoryResult.InvalidCandidate ->
                ReplaceConfirmedTournamentRosterResult.InvalidCandidate
            ReplaceConfirmedTournamentRosterRepositoryResult.BlockedByExistingMatches ->
                ReplaceConfirmedTournamentRosterResult.BlockedByExistingMatches
        }
}

private fun ConfirmedRosterReplacementCandidate.isStructurallyComplete(): Boolean {
    val expectedSlots = TeamSlot.SLOT_NUMBERS.toSet()
    return tournamentId.isNotBlank() &&
        teamNamesBySlotNumber.keys == expectedSlots &&
        rosterPlayersBySlotNumber.keys == expectedSlots &&
        rosterPlayersBySlotNumber.all { (slotNumber, players) ->
            players.all { player ->
                player.tournamentId == tournamentId && player.slotNumber == slotNumber
            }
        }
}
