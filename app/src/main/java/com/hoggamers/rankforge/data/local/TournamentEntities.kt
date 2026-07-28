package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sync_revisions")
data class SyncRevisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "local_revision")
    val localRevision: Int,
    @ColumnInfo(name = "base_cloud_revision")
    val baseCloudRevision: Int?,
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    @ColumnInfo(name = "organizer_name") val organizerName: String,
    @ColumnInfo(name = "organizer_contact_number") val organizerContactNumber: String,
    val status: String,
)

@Entity(
    tableName = "team_slots",
    primaryKeys = ["tournament_id", "slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id"])],
)
data class TeamSlotEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "slot_number") val slotNumber: Int,
    @ColumnInfo(name = "team_name") val teamName: String,
)

@Entity(
    tableName = "roster_players",
    primaryKeys = ["tournament_id", "slot_number", "roster_position"],
    foreignKeys = [
        ForeignKey(
            entity = TeamSlotEntity::class,
            parentColumns = ["tournament_id", "slot_number"],
            childColumns = ["tournament_id", "slot_number"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id", "slot_number"])],
)
data class RosterPlayerEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "slot_number") val slotNumber: Int,
    @ColumnInfo(name = "roster_position") val rosterPosition: Int,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = TournamentEntity::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tournament_id"])],
)
data class MatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "match_number") val matchNumber: Int,
    val date: String,
    @ColumnInfo(name = "map_name") val mapName: String,
    val status: String,
)

@Entity(
    tableName = "match_placements",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchPlacementEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    val position: Int,
)

@Entity(
    tableName = "match_kills",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchKillEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    val kills: Int,
)

@Entity(
    tableName = "match_draft_values",
    primaryKeys = ["match_id", "team_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchDraftValueEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
    @ColumnInfo(name = "placement_input") val placementInput: String,
    @ColumnInfo(name = "kills_input") val killsInput: String,
)

@Entity(
    tableName = "match_corrections",
    primaryKeys = ["match_id", "correction_index"],
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["match_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["match_id"])],
)
data class MatchCorrectionEntity(
    @ColumnInfo(name = "match_id") val matchId: String,
    @ColumnInfo(name = "correction_index") val correctionIndex: Int,
    @ColumnInfo(name = "previous_placements") val previousPlacements: String,
    @ColumnInfo(name = "previous_kills") val previousKills: String,
    @ColumnInfo(name = "corrected_placements") val correctedPlacements: String,
    @ColumnInfo(name = "corrected_kills") val correctedKills: String,
)
