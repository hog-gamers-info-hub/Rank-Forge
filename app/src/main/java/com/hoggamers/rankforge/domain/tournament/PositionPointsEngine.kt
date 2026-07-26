package com.hoggamers.rankforge.domain.tournament

/** Calculates position points from a confirmed placement. */
class PositionPointsEngine {
    operator fun invoke(confirmedPlacement: Int): Int {
        require(confirmedPlacement in VALID_PLACEMENTS) {
            "Confirmed placement must be between 1 and 12."
        }

        return when (confirmedPlacement) {
            1 -> 12
            2 -> 9
            3 -> 8
            4 -> 7
            5 -> 6
            6 -> 5
            7 -> 4
            8 -> 3
            9 -> 2
            10 -> 1
            11, 12 -> 0
            else -> error("Unreachable confirmed placement: $confirmedPlacement")
        }
    }

    private companion object {
        val VALID_PLACEMENTS = 1..12
    }
}
