package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupRotationPairingTeamEntryDraftDao {
    @Query(
        "SELECT * FROM group_rotation_pairing_team_entry_drafts " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<GroupRotationPairingTeamEntryDraftEntity>>

    @Query(
        "SELECT * FROM group_rotation_pairing_team_entry_drafts " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    suspend fun readByTournamentId(tournamentId: String): List<GroupRotationPairingTeamEntryDraftEntity>

    @Upsert
    suspend fun upsertAll(entries: List<GroupRotationPairingTeamEntryDraftEntity>)

    @Query(
        "DELETE FROM group_rotation_pairing_team_entry_drafts " +
            "WHERE tournament_id = :tournamentId",
    )
    suspend fun deleteByTournamentId(tournamentId: String)
}

