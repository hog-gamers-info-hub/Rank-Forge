package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult

data class DraftMatchCloudSyncSnapshot(
    val tournament: Tournament,
    val matches: List<Match>,
)

enum class DraftMatchCloudSyncStage {
    MATCHES,
}

sealed interface DraftMatchCloudSyncResult {
    data object Success : DraftMatchCloudSyncResult
    data object AuthenticationRequired : DraftMatchCloudSyncResult
    data object ValidationFailure : DraftMatchCloudSyncResult
    data object AuthorizationFailure : DraftMatchCloudSyncResult
    data object NetworkFailure : DraftMatchCloudSyncResult
    data class PartialFailure(
        val completedStage: DraftMatchCloudSyncStage,
    ) : DraftMatchCloudSyncResult
}

interface DraftMatchCloudSyncRepository {
    suspend fun sync(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncResult
}

fun interface DraftMatchCloudSyncAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<DraftMatchCloudSyncResult>
}
