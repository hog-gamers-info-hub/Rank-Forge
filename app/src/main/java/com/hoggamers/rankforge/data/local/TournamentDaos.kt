package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY date, id")
    fun observeAll(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments WHERE id = :tournamentId")
    fun observeById(tournamentId: String): Flow<TournamentEntity?>

    @Upsert
    suspend fun upsert(tournament: TournamentEntity)

    @Query("DELETE FROM tournaments WHERE id = :tournamentId")
    suspend fun deleteById(tournamentId: String)
}

@Dao
interface TeamSlotDao {
    @Query("SELECT * FROM team_slots WHERE tournament_id = :tournamentId ORDER BY slot_number")
    fun observeByTournamentId(tournamentId: String): Flow<List<TeamSlotEntity>>

    @Upsert
    suspend fun upsertAll(teamSlots: List<TeamSlotEntity>)

    @Query("DELETE FROM team_slots WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

@Dao
interface RosterPlayerDao {
    @Query(
        """
        SELECT * FROM roster_players
        WHERE tournament_id = :tournamentId AND slot_number = :slotNumber
        ORDER BY roster_position
        """,
    )
    fun observeByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayerEntity>>

    @Upsert
    suspend fun upsertAll(rosterPlayers: List<RosterPlayerEntity>)

    @Query(
        "DELETE FROM roster_players WHERE tournament_id = :tournamentId AND slot_number = :slotNumber",
    )
    suspend fun deleteByTournamentAndSlot(
        tournamentId: String,
        slotNumber: Int,
    )
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE tournament_id = :tournamentId ORDER BY match_number, id")
    fun observeByTournamentId(tournamentId: String): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun observeById(matchId: String): Flow<MatchEntity?>

    @Upsert
    suspend fun upsert(match: MatchEntity)

    @Query("DELETE FROM matches WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}
