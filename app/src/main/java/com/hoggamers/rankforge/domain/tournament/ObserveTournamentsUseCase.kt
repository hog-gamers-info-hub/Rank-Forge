package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class ObserveTournamentsUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(): Flow<List<Tournament>> =
        repository.observeAll()
}
