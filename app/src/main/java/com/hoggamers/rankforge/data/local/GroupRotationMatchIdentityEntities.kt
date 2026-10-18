package com.hoggamers.rankforge.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "group_rotation_match_identity_headers",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["tournament_id", "pairing_key"]),
    ],
)
data class GroupRotationMatchIdentityHeaderEntity(
    @androidx.room.PrimaryKey
    @androidx.room.ColumnInfo(name = "match_id")
    val matchId: String,
    @androidx.room.ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @androidx.room.ColumnInfo(name = "pairing_key")
    val pairingKey: String,
)

@Entity(
    tableName = "group_rotation_match_team_assignments",
    primaryKeys = ["match_id", "lobby_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = GroupRotationMatchIdentityHeaderEntity::class,
            parentColumns = ["match_id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupRotationTeamIdentityEntity::class,
            parentColumns = ["tournament_id", "team_uuid"],
            childColumns = ["tournament_id", "team_uuid"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["match_id"]),
        Index(value = ["tournament_id"]),
        Index(value = ["match_id", "team_uuid"], unique = true),
    ],
)
data class GroupRotationMatchTeamAssignmentEntity(
    @androidx.room.ColumnInfo(name = "match_id") val matchId: String,
    @androidx.room.ColumnInfo(name = "tournament_id") val tournamentId: String,
    @androidx.room.ColumnInfo(name = "lobby_slot_number") val lobbySlotNumber: Int,
    @androidx.room.ColumnInfo(name = "team_uuid") val teamUUID: String,
)

@Entity(
    tableName = "group_rotation_match_participant_results",
    primaryKeys = ["match_id", "team_uuid"],
    foreignKeys = [
        ForeignKey(
            entity = GroupRotationMatchIdentityHeaderEntity::class,
            parentColumns = ["match_id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GroupRotationTeamIdentityEntity::class,
            parentColumns = ["tournament_id", "team_uuid"],
            childColumns = ["tournament_id", "team_uuid"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["match_id"]),
        Index(value = ["tournament_id"]),
        Index(value = ["match_id", "placement"], unique = true),
    ],
)
data class GroupRotationMatchParticipantResultEntity(
    @androidx.room.ColumnInfo(name = "match_id") val matchId: String,
    @androidx.room.ColumnInfo(name = "tournament_id") val tournamentId: String,
    @androidx.room.ColumnInfo(name = "team_uuid") val teamUUID: String,
    @androidx.room.ColumnInfo(name = "participation_status") val participationStatus: String,
    val placement: Int?,
    val kills: Int,
    @androidx.room.ColumnInfo(name = "point_adjustment", defaultValue = "0") val pointAdjustment: Int = 0,
)

@Dao
interface GroupRotationMatchIdentityHeaderDao {
    @Query(
        "SELECT * FROM group_rotation_match_identity_headers WHERE match_id = :matchId",
    )
    suspend fun readByMatchId(matchId: String): GroupRotationMatchIdentityHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(header: GroupRotationMatchIdentityHeaderEntity)

    @Query("DELETE FROM group_rotation_match_identity_headers WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)

    @Query("DELETE FROM group_rotation_match_identity_headers WHERE tournament_id = :tournamentId")
    suspend fun deleteByTournamentId(tournamentId: String)
}

@Dao
interface GroupRotationMatchTeamAssignmentDao {
    @Query(
        "SELECT * FROM group_rotation_match_team_assignments " +
            "WHERE match_id = :matchId ORDER BY lobby_slot_number",
    )
    suspend fun readByMatchId(matchId: String): List<GroupRotationMatchTeamAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(assignments: List<GroupRotationMatchTeamAssignmentEntity>)

    @Query("DELETE FROM group_rotation_match_team_assignments WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)
}

@Dao
interface GroupRotationMatchParticipantResultDao {
    @Query(
        "SELECT * FROM group_rotation_match_participant_results " +
            "WHERE match_id = :matchId ORDER BY team_uuid",
    )
    suspend fun readByMatchId(matchId: String): List<GroupRotationMatchParticipantResultEntity>

    @Query("DELETE FROM group_rotation_match_participant_results WHERE match_id = :matchId")
    suspend fun deleteByMatchId(matchId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(results: List<GroupRotationMatchParticipantResultEntity>)
}
