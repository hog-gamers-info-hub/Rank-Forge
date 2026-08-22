package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveTournamentSummariesUseCase(
    private val observeSummaries: () -> Flow<List<TournamentSummary>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeSummaries)

    constructor(observeTournaments: ObserveTournamentsUseCase) : this(
        observeSummaries = {
            observeTournaments().map { tournaments ->
                tournaments.map { tournament ->
                    TournamentSummary(tournament, 0, 0, null)
                }
            }
        },
    )

    operator fun invoke(): Flow<List<TournamentSummary>> = observeSummaries()
}
