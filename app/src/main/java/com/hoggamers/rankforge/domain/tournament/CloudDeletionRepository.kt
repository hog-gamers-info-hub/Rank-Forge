package com.hoggamers.rankforge.domain.tournament

enum class CloudDeletionFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    STORAGE,
    VALIDATION,
    REMOTE,
}

sealed interface CloudDeletionStageResult {
    data object Success : CloudDeletionStageResult

    data class Failed(
        val category: CloudDeletionFailureCategory,
    ) : CloudDeletionStageResult
}

interface CloudDeletionRepository {
    suspend fun deleteMatchStorage(tournamentId: String, matchId: String): CloudDeletionStageResult
    suspend fun deleteMatchRemote(tournamentId: String, matchId: String): CloudDeletionStageResult
    suspend fun deleteTournamentStorage(tournamentId: String, matchIds: Set<String>): CloudDeletionStageResult
    suspend fun deleteTournamentRemote(tournamentId: String): CloudDeletionStageResult
}
