package com.hoggamers.rankforge.domain.tournament

sealed interface LocalDeletionResult {
    data object Deleted : LocalDeletionResult
    data object NotFound : LocalDeletionResult
    data object CleanupClaimLost : LocalDeletionResult
    data object FileCleanupFailed : LocalDeletionResult
}

interface LocalDeletionRepository {
    /** Trusted legacy/test compatibility only; secured callers must supply an owner. */
    @Deprecated("Use deleteMatchLocallyByOwner")
    suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult =
        error("Owner-scoped local deletion is required.")

    suspend fun deleteMatchLocallyByOwner(matchId: String, ownerUserId: String): LocalDeletionResult =
        error("Owner-scoped local deletion is not supported by this repository.")

    /** Trusted legacy/test compatibility only; secured callers must supply an owner. */
    @Deprecated("Use deleteTournamentLocallyByOwner")
    suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult =
        error("Owner-scoped local deletion is required.")

    suspend fun deleteTournamentLocallyByOwner(tournamentId: String, ownerUserId: String): LocalDeletionResult =
        error("Owner-scoped local deletion is not supported by this repository.")
}
