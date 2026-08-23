package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class TournamentCloudRestorationSummary(
    val id: String,
    val name: String,
    val date: String,
    val organizerName: String,
    val status: String,
)

data class RestoredRosterPlayer(
    val tournamentId: String,
    val slotNumber: Int,
    val rosterPosition: Int,
    val displayName: String,
)

data class TournamentCloudRestorationSnapshot(
    val tournament: Tournament,
    val slots: List<TeamSlot>,
    val players: List<RestoredRosterPlayer>,
    val cloudRevision: CloudRevision? = null,
)

enum class TournamentCloudRestorationFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    NOT_FOUND,
    NETWORK,
    VALIDATION,
}

sealed interface TournamentCloudRestorationResult {
    data class Available(
        val tournaments: List<TournamentCloudRestorationSummary>,
    ) : TournamentCloudRestorationResult

    data class Success(
        val tournamentName: String,
    ) : TournamentCloudRestorationResult

    data object AuthenticationRequired : TournamentCloudRestorationResult
    data object AuthorizationFailure : TournamentCloudRestorationResult
    data object ValidationFailure : TournamentCloudRestorationResult
    data object NetworkFailure : TournamentCloudRestorationResult
    data class Conflict(val conflict: RevisionConflict) : TournamentCloudRestorationResult
    data object LocalTransactionFailure : TournamentCloudRestorationResult
}

sealed interface TournamentCloudRestorationRemoteResult<out T> {
    data class Success<T>(val value: T) : TournamentCloudRestorationRemoteResult<T>

    data class Failure(
        val category: TournamentCloudRestorationFailureCategory,
    ) : TournamentCloudRestorationRemoteResult<Nothing>
}

interface TournamentCloudRestorationRepository {
    suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<
        List<TournamentCloudRestorationSummary>
        >

    suspend fun readOwnedTournament(
        tournamentId: String,
    ): TournamentCloudRestorationRemoteResult<TournamentCloudRestorationSnapshot>
}

interface TournamentRestorationLocalRepository {
    suspend fun restore(snapshot: TournamentCloudRestorationSnapshot)
    suspend fun detectTournamentDivergence(
        tournamentId: String,
        cloudRevision: CloudRevision,
    ): RevisionConflict? = null
}

interface TournamentCloudRestorationAction {
    suspend fun loadAvailable(): TournamentCloudRestorationResult

    suspend fun restore(tournamentId: String): QueueAwareActionResult<TournamentCloudRestorationResult>
}

fun interface TournamentCloudRestorationRetryAction {
    suspend fun executeForRetry(tournamentId: String): TournamentCloudRestorationResult
    suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String): TournamentCloudRestorationResult =
        throw SecurityException("Expected queue owner is required.")
}
