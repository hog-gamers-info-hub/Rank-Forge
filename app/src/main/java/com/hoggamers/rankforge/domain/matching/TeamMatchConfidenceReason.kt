package com.hoggamers.rankforge.domain.matching

enum class TeamMatchConfidenceReason {
    NO_SUGGESTIONS,
    BELOW_CONFIRMATION_THRESHOLD,
    MEETS_CONFIRMATION_THRESHOLD,
    MEETS_AUTOMATIC_THRESHOLD,
}
