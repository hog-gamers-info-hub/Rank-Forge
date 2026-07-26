package com.hoggamers.rankforge.domain.tournament

/** Calculates kill points from a confirmed kill value. */
class KillPointsEngine {
    operator fun invoke(confirmedKills: Int): Int {
        require(confirmedKills >= 0) {
            "Confirmed kills must be non-negative."
        }

        return confirmedKills
    }
}
