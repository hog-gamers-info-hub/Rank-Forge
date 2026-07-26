package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class ObserveRosterByTournamentUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(tournamentId: String): Flow<Map<Int, List<RosterPlayer>>> =
        repository.observeRosterByTournamentId(tournamentId)
}
