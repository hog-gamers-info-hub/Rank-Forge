package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTournamentSlotsUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(tournamentId: String): Flow<List<TeamSlot>> =
        repository.observeSlotsByTournamentId(tournamentId)
            .map { slots -> slots.sortedBy { it.slotNumber } }
}
