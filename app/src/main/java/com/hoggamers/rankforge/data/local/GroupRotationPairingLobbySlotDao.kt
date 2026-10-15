package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupRotationPairingLobbySlotDao {
    @Query(
        "SELECT * FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<GroupRotationPairingLobbySlotEntity>>

    @Query(
        "SELECT * FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    suspend fun readByTournamentId(tournamentId: String): List<GroupRotationPairingLobbySlotEntity>

    @Query(
        "SELECT * FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId AND pairing_key = :pairingKey " +
            "ORDER BY lobby_slot_number",
    )
    fun observeByTournamentAndPairing(
        tournamentId: String,
        pairingKey: String,
    ): Flow<List<GroupRotationPairingLobbySlotEntity>>

    @Query(
        "SELECT * FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId AND pairing_key = :pairingKey " +
            "ORDER BY lobby_slot_number",
    )
    suspend fun readByTournamentAndPairing(
        tournamentId: String,
        pairingKey: String,
    ): List<GroupRotationPairingLobbySlotEntity>

    @Upsert
    suspend fun upsertAll(assignments: List<GroupRotationPairingLobbySlotEntity>)

    @Query(
        "DELETE FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId AND pairing_key = :pairingKey",
    )
    suspend fun deleteByTournamentAndPairing(tournamentId: String, pairingKey: String)

    @Query(
        "DELETE FROM tournament_group_pairing_lobby_slots " +
            "WHERE tournament_id = :tournamentId",
    )
    suspend fun deleteByTournamentId(tournamentId: String)

    @Transaction
    suspend fun replaceForTournamentAndPairing(
        tournamentId: String,
        pairingKey: String,
        assignments: List<GroupRotationPairingLobbySlotEntity>,
    ) {
        require(assignments.all { assignment ->
            assignment.tournamentId == tournamentId && assignment.pairingKey == pairingKey
        }) {
            "All assignments must belong to tournament '$tournamentId' and pairing '$pairingKey'."
        }
        deleteByTournamentAndPairing(tournamentId, pairingKey)
        if (assignments.isNotEmpty()) {
            upsertAll(assignments)
        }
    }
}
