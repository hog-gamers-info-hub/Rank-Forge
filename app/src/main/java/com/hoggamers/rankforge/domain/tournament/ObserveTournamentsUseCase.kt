package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTournamentsUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(): Flow<List<Tournament>> =
        repository.observeAll().map { tournaments -> tournaments.asReversed() }
}
