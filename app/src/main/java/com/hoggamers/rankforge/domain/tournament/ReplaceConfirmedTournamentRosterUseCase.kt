package com.hoggamers.rankforge.domain.tournament

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
}

class ReplaceConfirmedTournamentRosterUseCase(
    private val repository: TournamentRepository,
    private val rosterValidator: RosterValidator,
) {
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

        return when (repository.replaceConfirmedTournamentRoster(candidate)) {
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
