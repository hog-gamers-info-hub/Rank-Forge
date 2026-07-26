package com.hoggamers.rankforge.domain.tournament

data class CumulativeTournamentStanding(
    val teamSlotNumber: Int,
    val totalPositionPoints: Int,
    val totalKillPoints: Int,
    val totalPoints: Int,
    val firstPlaceFinishes: Int,
    val latestMatchPlacement: Int,
    val matchesIncluded: Int,
)

/**
 * Calculates finalized-match standings in stable team-slot order.
 *
 * Team slots are inferred from confirmed result rows because this calculation accepts matches,
 * not a separate tournament roster.
 */
class CumulativeTournamentStandingsEngine(
    private val positionPointsEngine: PositionPointsEngine = PositionPointsEngine(),
    private val killPointsEngine: KillPointsEngine = KillPointsEngine(),
    private val matchTotalEngine: MatchTotalEngine = MatchTotalEngine(),
) {
    operator fun invoke(matches: List<Match>): List<CumulativeTournamentStanding> {
        val finalizedMatches = matches
            .asSequence()
            .filter { it.status == MatchStatus.FINALIZED }
            .distinctBy { it.id }
            .sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id })
            .toList()

        require(finalizedMatches.size <= MAX_MATCHES_PER_TOURNAMENT) {
            "A tournament can include at most $MAX_MATCHES_PER_TOURNAMENT finalized matches."
        }

        val totalsByTeamSlot = mutableMapOf<Int, MutableStandingTotals>()
        finalizedMatches.forEach { match ->
            val killsByTeamSlot = match.kills.associateBy { it.teamSlotNumber }
            match.placements.forEach { placement ->
                val confirmedKills = requireNotNull(killsByTeamSlot[placement.teamSlotNumber]) {
                    "A finalized placement must have a confirmed kill value."
                }.kills

                val positionPoints = positionPointsEngine(placement.position)
                val killPoints = killPointsEngine(confirmedKills)
                val matchTotal = matchTotalEngine(placement.position, confirmedKills)

                val totals = totalsByTeamSlot.getOrPut(placement.teamSlotNumber) {
                    MutableStandingTotals()
                }

                totals.totalPositionPoints += positionPoints
                totals.totalKillPoints += killPoints
                totals.totalPoints += matchTotal
                if (placement.position == 1) totals.firstPlaceFinishes++
                totals.latestMatchPlacement = placement.position
                totals.matchesIncluded++
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
                    latestMatchPlacement = checkNotNull(totals.latestMatchPlacement),
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
        var matchesIncluded: Int = 0
    }
}
