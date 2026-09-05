package com.hoggamers.rankforge.domain.auth

enum class AccountDeletionFailureCategory {
    NO_SESSION,
    NETWORK,
    AUTHENTICATION,
    SERVER,
    UNKNOWN,
}

sealed interface AccountDeletionResult {
    data object Success : AccountDeletionResult

    data class Failure(
        val category: AccountDeletionFailureCategory,
    ) : AccountDeletionResult
}

interface AccountDeletionRepository {
    suspend fun deleteCurrentAccount(): AccountDeletionResult
}
