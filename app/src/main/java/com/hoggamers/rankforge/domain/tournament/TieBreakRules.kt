package com.hoggamers.rankforge.domain.tournament

data class TieBreakStanding(
    val standing: CumulativeTournamentStanding,
    val isCompleteTie: Boolean,
)

/** Orders standings by the approved tie-break criteria without resolving complete ties competitively. */
class TieBreakRules {
    operator fun invoke(standings: List<CumulativeTournamentStanding>): List<TieBreakStanding> {
        val completeTieKeys = standings
            .groupingBy(::tieBreakKey)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys

        return standings
            .sortedWith(
                compareByDescending<CumulativeTournamentStanding> { it.totalPoints }
                    .thenByDescending { it.firstPlaceFinishes }
                    .thenByDescending { it.totalKillPoints }
                    .thenBy { it.latestMatchPlacement == null }
                    .thenBy { it.latestMatchPlacement }
                    .thenBy { it.teamSlotNumber },
            )
            .map { standing ->
                TieBreakStanding(
                    standing = standing,
                    isCompleteTie = tieBreakKey(standing) in completeTieKeys,
                )
            }
    }

    private fun tieBreakKey(standing: CumulativeTournamentStanding): TieBreakKey =
        TieBreakKey(
            totalPoints = standing.totalPoints,
            firstPlaceFinishes = standing.firstPlaceFinishes,
            totalKills = standing.totalKillPoints,
            latestMatchPlacement = standing.latestMatchPlacement,
        )

    private data class TieBreakKey(
        val totalPoints: Int,
        val firstPlaceFinishes: Int,
        val totalKills: Int,
        val latestMatchPlacement: Int?,
    )
}
