package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class ObserveMatchesUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(tournamentId: String): Flow<List<Match>> =
        repository.observeMatchesByTournamentId(tournamentId)
}
