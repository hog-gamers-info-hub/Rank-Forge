package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

interface TournamentRepository {
    suspend fun create(tournament: Tournament)

    fun observeAll(): Flow<List<Tournament>>
}
