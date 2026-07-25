package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

interface TournamentRepository {
    suspend fun create(tournament: Tournament)

    fun observeAll(): Flow<List<Tournament>>

    fun observeById(tournamentId: String): Flow<Tournament?>

    fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>>

    suspend fun saveTeamNames(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    )

    fun observeRosterByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>>

    suspend fun saveRoster(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    )
}
