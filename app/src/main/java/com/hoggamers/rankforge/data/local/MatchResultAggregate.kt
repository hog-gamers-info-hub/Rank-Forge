package com.hoggamers.rankforge.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class MatchResultAggregate(
    @Embedded
    val match: MatchEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "match_id",
    )
    val placements: List<MatchPlacementEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "match_id",
    )
    val kills: List<MatchKillEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "match_id",
    )
    val participantResults: List<MatchParticipantResultEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "match_id",
    )
    val corrections: List<MatchCorrectionEntity>,
)
