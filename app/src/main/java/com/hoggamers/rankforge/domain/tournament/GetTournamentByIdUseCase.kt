package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class GetTournamentByIdUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(tournamentId: String): Flow<Tournament?> =
        repository.observeById(tournamentId)
}
