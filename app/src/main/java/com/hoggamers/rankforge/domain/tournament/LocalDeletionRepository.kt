package com.hoggamers.rankforge.domain.tournament

sealed interface LocalDeletionResult {
    data object Deleted : LocalDeletionResult
    data object NotFound : LocalDeletionResult
    data object FileCleanupFailed : LocalDeletionResult
}

interface LocalDeletionRepository {
    suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult

    suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult
}
