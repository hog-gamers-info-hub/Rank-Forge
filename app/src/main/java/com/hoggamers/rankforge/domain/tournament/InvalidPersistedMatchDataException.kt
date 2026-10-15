package com.hoggamers.rankforge.domain.tournament

/** Raised when persisted match data cannot be exposed as a complete Match. */
class InvalidPersistedMatchDataException : IllegalStateException(
    "Persisted match data failed validation",
)
