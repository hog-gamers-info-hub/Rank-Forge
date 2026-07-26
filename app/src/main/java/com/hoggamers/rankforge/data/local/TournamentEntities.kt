package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
