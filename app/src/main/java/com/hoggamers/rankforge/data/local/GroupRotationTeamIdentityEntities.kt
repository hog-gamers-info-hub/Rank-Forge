package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "group_rotation_team_identities",
    primaryKeys = ["tournament_id", "team_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id", "normalized_name"], unique = false),
    ],
)
data class GroupRotationTeamIdentityEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "team_uuid") val teamUUID: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
)

@Dao
interface GroupRotationTeamIdentityDao {
    @Query(
        "SELECT * FROM group_rotation_team_identities " +
            "WHERE tournament_id = :tournamentId ORDER BY team_uuid",
    )
    suspend fun readByTournamentId(tournamentId: String): List<GroupRotationTeamIdentityEntity>

    @Query(
        "SELECT * FROM group_rotation_team_identities " +
            "WHERE tournament_id = :tournamentId ORDER BY team_uuid",
    )
    fun observeByTournamentId(tournamentId: String): Flow<List<GroupRotationTeamIdentityEntity>>

    @Upsert
    suspend fun upsertAll(teams: List<GroupRotationTeamIdentityEntity>)

    @Query("DELETE FROM group_rotation_team_identities WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

@Entity(
    tableName = "group_rotation_pairing_team_identity_assignments",
    primaryKeys = ["tournament_id", "pairing_key", "lobby_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentGroupPairingEntity::class,
            parentColumns = ["tournament_id", "pairing_key"],
            childColumns = ["tournament_id", "pairing_key"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupRotationTeamIdentityEntity::class,
            parentColumns = ["tournament_id", "team_uuid"],
            childColumns = ["tournament_id", "team_uuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id", "pairing_key"]),
        Index(value = ["tournament_id", "team_uuid"]),
        Index(value = ["tournament_id", "pairing_key", "team_uuid"], unique = true),
    ],
)
data class GroupRotationPairingTeamIdentityAssignmentEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "pairing_key") val pairingKey: String,
    @ColumnInfo(name = "lobby_slot_number") val lobbySlotNumber: Int,
    @ColumnInfo(name = "team_uuid") val teamUUID: String,
    @ColumnInfo(name = "name_cleared") val nameCleared: Boolean = false,
)

@Dao
interface GroupRotationPairingTeamIdentityAssignmentDao {
    @Query(
        "SELECT * FROM group_rotation_pairing_team_identity_assignments " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    suspend fun readByTournamentId(tournamentId: String): List<GroupRotationPairingTeamIdentityAssignmentEntity>

    @Query(
        "SELECT * FROM group_rotation_pairing_team_identity_assignments " +
            "WHERE tournament_id = :tournamentId " +
            "ORDER BY pairing_key, lobby_slot_number",
    )
    fun observeByTournamentId(
        tournamentId: String,
    ): Flow<List<GroupRotationPairingTeamIdentityAssignmentEntity>>

    @Upsert
    suspend fun upsertAll(assignments: List<GroupRotationPairingTeamIdentityAssignmentEntity>)

    @Query(
        "DELETE FROM group_rotation_pairing_team_identity_assignments " +
            "WHERE tournament_id = :tournamentId AND pairing_key = :pairingKey",
    )
    suspend fun deleteByTournamentIdAndPairing(tournamentId: String, pairingKey: String)

    @Query(
        "DELETE FROM group_rotation_pairing_team_identity_assignments " +
            "WHERE tournament_id = :tournamentId",
    )
    suspend fun deleteByTournamentId(tournamentId: String)
}
