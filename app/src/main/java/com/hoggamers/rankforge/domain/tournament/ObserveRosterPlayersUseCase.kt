package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class ObserveRosterPlayersUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>> = repository.observeRosterByTournamentAndSlot(
        tournamentId = tournamentId,
        slotNumber = slotNumber,
    )
}
