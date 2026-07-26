package com.hoggamers.rankforge.domain.tournament

/** Calculates a match total from confirmed placement and kill values. */
class MatchTotalEngine(
    private val positionPointsEngine: PositionPointsEngine = PositionPointsEngine(),
    private val killPointsEngine: KillPointsEngine = KillPointsEngine(),
) {
    operator fun invoke(confirmedPlacement: Int, confirmedKills: Int): Int =
        positionPointsEngine(confirmedPlacement) + killPointsEngine(confirmedKills)
}
