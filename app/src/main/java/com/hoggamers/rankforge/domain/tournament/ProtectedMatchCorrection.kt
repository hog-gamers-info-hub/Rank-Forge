package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class ProtectedMatchCorrectionRequest(
    val tournament: Tournament,
    val match: Match,
    val placements: List<MatchPlacement>,
    val kills: List<MatchKill>,
    val expectedRevision: Int,
    val participantResults: List<MatchParticipantResult> = emptyList(),
)

/**
 * Returns the immutable participant identity represented by a finalized local match.
 *
 * Local match results use team-slot identity as their persisted result key. A finalized
 * correction may replace values for these slots, but may not change the participant set.
 */
internal fun Match.finalizedParticipantSlotsOrNull(): Set<Int>? {
    return finalizedParticipantResultsOrNull()?.map { it.teamSlotNumber }?.toSet()
}

/**
 * Returns the persisted finalized participant snapshot, or a participated-only
 * snapshot for legacy matches that predate the participant result table.
 * The current tournament TeamSlot roster is intentionally never consulted.
 */
internal fun Match.finalizedParticipantResultsOrNull(): List<MatchParticipantResult>? {
    if (status != MatchStatus.FINALIZED) return null
    val snapshot = if (participantResults.isNotEmpty()) {
        participantResults
    } else {
        val killsBySlot = kills.associateBy { it.teamSlotNumber }
        val legacy = placements.mapNotNull { placement ->
            killsBySlot[placement.teamSlotNumber]?.let { kill -> placement to kill }
        }
        runCatching {
            legacy.map { (placement, kill) ->
                MatchParticipantResult(
                    teamSlotNumber = placement.teamSlotNumber,
                    participationStatus = MatchParticipationStatus.PARTICIPATED,
                    placement = placement.position,
                    kills = kill.kills,
                )
            }
        }.getOrElse { return null }
    }
    if (snapshot.isEmpty() || snapshot.size > TeamSlot.MAX_SLOT_NUMBER) return null
    if (snapshot.map { it.teamSlotNumber }.toSet().size != snapshot.size) return null
    if (snapshot.any { it.teamSlotNumber !in TeamSlot.SLOT_NUMBERS }) return null

    val participated = snapshot.filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
    if (participated.isEmpty()) return null
    if (participated.mapNotNull { it.placement }.toSet() != (1..participated.size).toSet()) return null
    if (participated.any { it.placement == null || it.kills < 0 }) return null
    if (snapshot.any {
            it.participationStatus == MatchParticipationStatus.NO_SHOW &&
                (it.placement != null || it.kills != 0)
        }) return null
    return snapshot.sortedBy { it.teamSlotNumber }
}

internal fun isValidCorrectionSnapshot(
    previous: List<MatchParticipantResult>?,
    corrected: List<MatchParticipantResult>,
): Boolean {
    if (previous == null || corrected.size != previous.size) return false
    if (corrected.map { it.teamSlotNumber }.toSet() != previous.map { it.teamSlotNumber }.toSet()) return false
    val participated = corrected.filter { it.participationStatus == MatchParticipationStatus.PARTICIPATED }
    return participated.isNotEmpty() &&
        participated.mapNotNull { it.placement }.toSet() == (1..participated.size).toSet() &&
        participated.all { it.placement != null && it.kills >= 0 } &&
        corrected.filter { it.participationStatus == MatchParticipationStatus.NO_SHOW }
            .all { it.placement == null && it.kills == 0 }
}

internal fun correctedMatchPlacements(
    results: List<MatchParticipantResult>,
): List<MatchPlacement> = results.mapNotNull { result ->
    result.placement?.let { MatchPlacement(result.teamSlotNumber, it) }
}

internal fun correctedMatchKills(
    results: List<MatchParticipantResult>,
): List<MatchKill> = results.filter { it.placement != null }
    .map { MatchKill(it.teamSlotNumber, it.kills) }

sealed interface ProtectedMatchCorrectionResult {
    data class Success(val revision: Int) : ProtectedMatchCorrectionResult
    data class AlreadyCorrected(val revision: Int) : ProtectedMatchCorrectionResult
    data object AuthenticationRequired : ProtectedMatchCorrectionResult
    data object AuthorizationFailure : ProtectedMatchCorrectionResult
    data object NetworkFailure : ProtectedMatchCorrectionResult
    data object ValidationFailure : ProtectedMatchCorrectionResult
    data object MatchNotFinalized : ProtectedMatchCorrectionResult
    data class Conflict(val conflict: RevisionConflict) : ProtectedMatchCorrectionResult
}

fun interface ProtectedMatchCorrectionAction {
    suspend operator fun invoke(request: ProtectedMatchCorrectionRequest): ProtectedMatchCorrectionResult
}
