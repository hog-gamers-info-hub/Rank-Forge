package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult

data class FinalizedMatchCloudSyncSnapshot(
    val tournament: Tournament,
    val matches: List<Match>,
)

enum class FinalizedMatchCloudSyncStage {
    MATCHES,
}

sealed interface FinalizedMatchCloudSyncResult {
    data object Success : FinalizedMatchCloudSyncResult
    data object AuthenticationRequired : FinalizedMatchCloudSyncResult
    data object ValidationFailure : FinalizedMatchCloudSyncResult
    data object AuthorizationFailure : FinalizedMatchCloudSyncResult
    data object NetworkFailure : FinalizedMatchCloudSyncResult
    data class PartialFailure(
        val completedStage: FinalizedMatchCloudSyncStage,
    ) : FinalizedMatchCloudSyncResult
}

interface FinalizedMatchCloudSyncRepository {
    suspend fun sync(snapshot: FinalizedMatchCloudSyncSnapshot): FinalizedMatchCloudSyncResult
}

fun interface FinalizedMatchCloudSyncAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<FinalizedMatchCloudSyncResult>
}

fun interface FinalizedMatchCloudSyncRetryAction {
    suspend fun executeForRetry(tournamentId: String): FinalizedMatchCloudSyncResult
}
