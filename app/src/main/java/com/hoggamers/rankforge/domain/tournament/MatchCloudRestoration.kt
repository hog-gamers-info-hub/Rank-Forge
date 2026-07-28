package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult

data class MatchCloudRestorationSnapshot(
    val tournamentId: String,
    val matches: List<Match>,
)

enum class MatchCloudRestorationFailureCategory { AUTHENTICATION, AUTHORIZATION, NETWORK, VALIDATION }

sealed interface MatchCloudRestorationRemoteResult<out T> {
    data class Success<T>(val value: T) : MatchCloudRestorationRemoteResult<T>
    data class Failure(val category: MatchCloudRestorationFailureCategory) : MatchCloudRestorationRemoteResult<Nothing>
}

interface MatchCloudRestorationRepository {
    suspend fun readOwnedMatches(tournamentId: String): MatchCloudRestorationRemoteResult<MatchCloudRestorationSnapshot>
}

interface MatchRestorationLocalRepository {
    suspend fun replaceMatches(snapshot: MatchCloudRestorationSnapshot)
}

sealed interface MatchCloudRestorationResult {
    data object Success : MatchCloudRestorationResult
    data object NoCloudMatches : MatchCloudRestorationResult
    data object AuthenticationRequired : MatchCloudRestorationResult
    data object AuthorizationFailure : MatchCloudRestorationResult
    data object ValidationFailure : MatchCloudRestorationResult
    data object NetworkFailure : MatchCloudRestorationResult
    data object LocalTransactionFailure : MatchCloudRestorationResult
}

fun interface MatchCloudRestorationAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<MatchCloudRestorationResult>
}
