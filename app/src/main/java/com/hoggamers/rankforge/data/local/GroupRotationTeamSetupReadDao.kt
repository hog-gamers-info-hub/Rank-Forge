package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

data class GroupRotationTeamSetupReadRow(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "pairing_key") val pairingKey: String,
    @ColumnInfo(name = "lobby_slot_number") val lobbySlotNumber: Int,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    @ColumnInfo(name = "resolved_team_slot_number") val resolvedTeamSlotNumber: Int?,
    @ColumnInfo(name = "team_name") val teamName: String?,
)

@Dao
interface GroupRotationTeamSetupReadDao {
    @Query(
        "SELECT * FROM tournaments " +
            "WHERE id = :tournamentId AND owner_user_id = :ownerUserId",
    )
    suspend fun readOwnedTournament(
        tournamentId: String,
        ownerUserId: String,
    ): TournamentEntity?

    @Query(
        "SELECT * FROM tournament_group_pairings " +
            "WHERE tournament_id = :tournamentId ORDER BY pairing_key",
    )
    suspend fun readPairings(tournamentId: String): List<TournamentGroupPairingEntity>

    @Query(
        "SELECT * FROM team_slots WHERE tournament_id = :tournamentId ORDER BY slot_number",
    )
    suspend fun readTeamSlots(tournamentId: String): List<TeamSlotEntity>

    @Query(
        "SELECT mappings.tournament_id, mappings.pairing_key, " +
            "mappings.lobby_slot_number, mappings.team_slot_number, " +
            "slots.slot_number AS resolved_team_slot_number, slots.team_name " +
            "FROM tournament_group_pairing_lobby_slots mappings " +
            "LEFT JOIN team_slots slots ON slots.tournament_id = mappings.tournament_id " +
            "AND slots.slot_number = mappings.team_slot_number " +
            "WHERE mappings.tournament_id = :tournamentId " +
            "ORDER BY mappings.pairing_key, mappings.lobby_slot_number",
    )
    suspend fun readMappingRows(tournamentId: String): List<GroupRotationTeamSetupReadRow>
}
