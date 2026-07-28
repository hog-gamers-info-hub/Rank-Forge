package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class MatchCloudRestorationSnapshot(
    val tournamentId: String,
    val matches: List<Match>,
    val cloudRevision: CloudRevision? = null,
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

    /** Replaces draft rows only; finalized rows are intentionally preserved. */
    suspend fun replaceDraftMatches(snapshot: MatchCloudRestorationSnapshot): Unit =
        error("Draft-only match replacement is not supported by this repository.")
    suspend fun detectMatchDivergence(
        tournamentId: String,
        cloudRevision: CloudRevision,
    ): RevisionConflict? = null
}

sealed interface MatchCloudRestorationResult {
    data object Success : MatchCloudRestorationResult
    data object NoCloudMatches : MatchCloudRestorationResult
    data object AuthenticationRequired : MatchCloudRestorationResult
    data object AuthorizationFailure : MatchCloudRestorationResult
    data object ValidationFailure : MatchCloudRestorationResult
    data object NetworkFailure : MatchCloudRestorationResult
    data class Conflict(
        val conflict: RevisionConflict,
        val context: ConflictResolutionContext? = null,
    ) : MatchCloudRestorationResult
    data object LocalTransactionFailure : MatchCloudRestorationResult
}

fun interface MatchCloudRestorationAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<MatchCloudRestorationResult>
}

fun interface MatchCloudRestorationRetryAction {
    suspend fun executeForRetry(tournamentId: String): MatchCloudRestorationResult
}
