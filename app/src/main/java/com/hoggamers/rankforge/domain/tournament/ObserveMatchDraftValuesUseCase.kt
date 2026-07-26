package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

class ObserveMatchDraftValuesUseCase(
    private val repository: TournamentRepository,
) {
    operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = repository.observeDraftMatchValues(
        tournamentId = tournamentId,
        matchId = matchId,
    )
}
