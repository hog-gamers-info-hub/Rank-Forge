package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "group_rotation_pairing_team_entry_drafts",
    primaryKeys = ["tournament_id", "pairing_key", "lobby_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentGroupPairingEntity::class,
            parentColumns = ["tournament_id", "pairing_key"],
            childColumns = ["tournament_id", "pairing_key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id", "pairing_key"]),
        Index(value = ["tournament_id", "lobby_slot_number"]),
    ],
)
data class GroupRotationPairingTeamEntryDraftEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "pairing_key") val pairingKey: String,
    @ColumnInfo(name = "lobby_slot_number") val lobbySlotNumber: Int,
    @ColumnInfo(name = "raw_team_name") val rawTeamName: String,
)

