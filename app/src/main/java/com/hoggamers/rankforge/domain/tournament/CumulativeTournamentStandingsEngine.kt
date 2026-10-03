package com.hoggamers.rankforge.domain.tournament

data class CumulativeTournamentStanding(
    val teamSlotNumber: Int,
    val totalPositionPoints: Int,
    val totalKillPoints: Int,
    val totalPoints: Int,
    val firstPlaceFinishes: Int,
    val latestMatchPlacement: Int?,
    val matchesIncluded: Int,
) {
    val matchesPlayed: Int
        get() = matchesIncluded
}

/**
 * Calculates finalized-match standings in stable team-slot order.
 *
 * Callers may provide the persisted tournament roster so zero-total and resting teams remain
 * visible. When omitted, the legacy participant-snapshot behavior is retained.
 */
class CumulativeTournamentStandingsEngine(
    private val positionPointsEngine: PositionPointsEngine = PositionPointsEngine(),
    private val killPointsEngine: KillPointsEngine = KillPointsEngine(),
) {
    operator fun invoke(
        matches: List<Match>,
        expectedTeamSlots: Collection<TeamSlot>? = null,
    ): List<CumulativeTournamentStanding> {
        val finalizedMatches = matches
            .asSequence()
            .filter { it.status == MatchStatus.FINALIZED }
            .distinctBy { it.id }
            .sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id })
            .toList()

        require(finalizedMatches.size <= MAX_MATCHES_PER_TOURNAMENT) {
            "A tournament can include at most $MAX_MATCHES_PER_TOURNAMENT finalized matches."
        }

        val expectedSlotNumbers = expectedTeamSlots?.map { slot ->
            require(slot.slotNumber in TeamSlot.TOURNAMENT_SLOT_NUMBERS) {
                "Expected team slot number must be between 1 and 24."
            }
            slot.slotNumber
        }?.also { slotNumbers ->
            require(slotNumbers.size == slotNumbers.toSet().size) {
                "Expected team slots must not contain duplicates."
            }
        }?.toSet()

        val totalsByTeamSlot = expectedSlotNumbers
            ?.associateWith { MutableStandingTotals() }
            ?.toMutableMap()
            ?: mutableMapOf()
        finalizedMatches.forEach { match ->
            val participantResults = match.finalizedParticipantResultsOrNull()
                ?: run {
                    match.placements.forEach { placement ->
                        requireNotNull(match.kills.firstOrNull {
                            it.teamSlotNumber == placement.teamSlotNumber
                        }) {
                            "A finalized placement must have a confirmed kill value."
                        }
                    }
                    error("A finalized match must have a valid participant snapshot.")
                }
            require(participantResults.size <= TeamSlot.MAX_SLOT_NUMBER) {
                "A finalized match can contain at most ${TeamSlot.MAX_SLOT_NUMBER} participant rows."
            }
            participantResults.forEach { result ->
                require(expectedSlotNumbers == null || result.teamSlotNumber in expectedSlotNumbers) {
                    "A finalized participant must belong to the expected tournament roster."
                }
                val totals = totalsByTeamSlot.getOrPut(result.teamSlotNumber) {
                    MutableStandingTotals()
                }
                if (result.participationStatus == MatchParticipationStatus.PARTICIPATED) {
                    val placement = requireNotNull(result.placement)
                    totals.totalPositionPoints += positionPointsEngine(placement)
                    totals.totalKillPoints += killPointsEngine(result.kills)
                    totals.totalPoints += result.totalPoints
                    if (placement == 1) totals.firstPlaceFinishes++
                    totals.latestMatchPlacement = placement
                    totals.bestPlacement = minOf(totals.bestPlacement ?: placement, placement)
                    totals.matchesIncluded++
                }
            }
        }

        return totalsByTeamSlot
            .toSortedMap()
            .map { (teamSlotNumber, totals) ->
                CumulativeTournamentStanding(
                    teamSlotNumber = teamSlotNumber,
                    totalPositionPoints = totals.totalPositionPoints,
                    totalKillPoints = totals.totalKillPoints,
                    totalPoints = totals.totalPoints,
                    firstPlaceFinishes = totals.firstPlaceFinishes,
                    latestMatchPlacement = totals.latestMatchPlacement,
                    matchesIncluded = totals.matchesIncluded,
                )
            }
    }

    private class MutableStandingTotals {
        var totalPositionPoints: Int = 0
        var totalKillPoints: Int = 0
        var totalPoints: Int = 0
        var firstPlaceFinishes: Int = 0
        var latestMatchPlacement: Int? = null
        var bestPlacement: Int? = null
        var matchesIncluded: Int = 0
    }
}
