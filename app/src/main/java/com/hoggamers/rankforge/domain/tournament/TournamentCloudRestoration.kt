package com.hoggamers.rankforge.domain.tournament

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
)

enum class TournamentCloudRestorationFailureCategory {
    AUTHENTICATION,
    AUTHORIZATION,
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
}

interface TournamentCloudRestorationAction {
    suspend fun loadAvailable(): TournamentCloudRestorationResult

    suspend fun restore(tournamentId: String): TournamentCloudRestorationResult
}
