package com.hoggamers.rankforge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(
    tableName = "deletion_intents",
    primaryKeys = ["target_type", "target_id"],
    indices = [Index(value = ["tournament_id"]), Index(value = ["phase"])],
)
data class DeletionIntentEntity(
    @ColumnInfo(name = "target_type")
    val targetType: String,
    @ColumnInfo(name = "target_id")
    val targetId: String,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: String,
    @ColumnInfo(name = "owner_user_id")
    val ownerUserId: String,
    val phase: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
