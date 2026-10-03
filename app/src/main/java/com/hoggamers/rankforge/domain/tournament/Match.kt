package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate

const val MAX_MATCHES_PER_TOURNAMENT = 18

fun nextAvailableMatchNumber(existingMatchNumbers: Collection<Int>): Int? {
    val occupiedNumbers = existingMatchNumbers.toSet()
    val nextSequentialNumber = existingMatchNumbers.maxOrNull()?.plus(1) ?: 1
    return if (nextSequentialNumber <= MAX_MATCHES_PER_TOURNAMENT) {
        nextSequentialNumber
    } else {
        (1..MAX_MATCHES_PER_TOURNAMENT).firstOrNull { it !in occupiedNumbers }
    }
}

data class Match(
    val id: String,
    val tournamentId: String,
    val matchNumber: Int,
    val date: LocalDate,
    val mapName: String,
    val status: MatchStatus,
    val placements: List<MatchPlacement> = emptyList(),
    val kills: List<MatchKill> = emptyList(),
    val correctionHistory: List<MatchCorrectionRecord> = emptyList(),
    /** Complete finalized participant identity/status snapshot; empty for legacy or draft matches. */
    val participantResults: List<MatchParticipantResult> = emptyList(),
    /** The selected group pairing for a Group Rotation match; null for Standard matches. */
    val groupPairing: GroupPairing? = null,
)

data class MatchPlacement(
    val teamSlotNumber: Int,
    val position: Int,
)

data class MatchKill(
    val teamSlotNumber: Int,
    val kills: Int,
)

data class MatchCorrectionRecord(
    val previousPlacements: List<MatchPlacement>,
    val previousKills: List<MatchKill>,
    val correctedPlacements: List<MatchPlacement>,
    val correctedKills: List<MatchKill>,
    val previousParticipantResults: List<MatchParticipantResult> = emptyList(),
    val correctedParticipantResults: List<MatchParticipantResult> = emptyList(),
)

enum class MatchStatus {
    DRAFT,
    FINALIZED,
}

