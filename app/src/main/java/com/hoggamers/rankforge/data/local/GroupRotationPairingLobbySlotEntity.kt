package com.hoggamers.rankforge.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "tournament_group_pairing_lobby_slots",
    primaryKeys = ["tournament_id", "pairing_key", "lobby_slot_number"],
    foreignKeys = [
        ForeignKey(
            entity = TournamentGroupPairingEntity::class,
            parentColumns = ["tournament_id", "pairing_key"],
            childColumns = ["tournament_id", "pairing_key"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TeamSlotEntity::class,
            parentColumns = ["tournament_id", "slot_number"],
            childColumns = ["tournament_id", "team_slot_number"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["tournament_id", "pairing_key"]),
        Index(value = ["tournament_id", "team_slot_number"]),
        Index(value = ["tournament_id", "pairing_key", "team_slot_number"], unique = true),
    ],
)
data class GroupRotationPairingLobbySlotEntity(
    @ColumnInfo(name = "tournament_id") val tournamentId: String,
    @ColumnInfo(name = "pairing_key") val pairingKey: String,
    @ColumnInfo(name = "lobby_slot_number") val lobbySlotNumber: Int,
    @ColumnInfo(name = "team_slot_number") val teamSlotNumber: Int,
)
