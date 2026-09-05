package com.hoggamers.rankforge.domain.auth

enum class AccountDeletionPhase {
    REMOTE_REQUESTED,
    REMOTE_CONFIRMED,
    LOCAL_CLEANUP_COMPLETE,
}

data class AccountDeletionMarker(
    val ownerUserId: String,
    val phase: AccountDeletionPhase,
    val updatedAtEpochMillis: Long,
)

sealed interface AccountDeletionLocalCleanupResult {
    data object Completed : AccountDeletionLocalCleanupResult
    data object Failed : AccountDeletionLocalCleanupResult
}

interface AccountDeletionLocalCleanupRepository {
    suspend fun readMarker(): AccountDeletionMarker?

    suspend fun markRemoteRequested(ownerUserId: String)

    suspend fun markRemoteConfirmed(ownerUserId: String)

    suspend fun purgeLocalDataForOwner(ownerUserId: String): AccountDeletionLocalCleanupResult

    suspend fun markLocalCleanupComplete(ownerUserId: String)

    suspend fun clearMarker(ownerUserId: String)
}
