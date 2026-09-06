package com.hoggamers.rankforge.domain.auth

enum class AccountDeletionFailureCategory {
    NO_SESSION,
    NETWORK,
    AUTHENTICATION,
    SERVER,
    UNKNOWN,
}

enum class AccountDeletionRequestDisposition {
    NOT_DISPATCHED,
    MAY_HAVE_REACHED_SERVER,
}

enum class AccountDeletionRemoteAccountStatus {
    PRESENT,
    DELETED,
    UNKNOWN,
}

sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult

    data class Failure(
        val category: AccountDeletionFailureCategory,
        val requestDisposition: AccountDeletionRequestDisposition =
            AccountDeletionRequestDisposition.NOT_DISPATCHED,
    ) : AccountDeletionResult
}

interface AccountDeletionRepository {
    suspend fun deleteCurrentAccount(): AccountDeletionResult

    suspend fun probeCurrentAccount(): AccountDeletionRemoteAccountStatus =
        AccountDeletionRemoteAccountStatus.UNKNOWN
}
