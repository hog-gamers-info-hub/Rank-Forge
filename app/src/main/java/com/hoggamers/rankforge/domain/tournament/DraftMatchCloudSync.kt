package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class DraftMatchCloudSyncSnapshot(
    val tournament: Tournament,
    val matches: List<Match>,
    val expectedCloudRevision: Int? = null,
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
    data class Conflict(
        val conflict: RevisionConflict,
        val context: ConflictResolutionContext? = null,
    ) : DraftMatchCloudSyncResult
    data class PartialFailure(
        val completedStage: DraftMatchCloudSyncStage,
    ) : DraftMatchCloudSyncResult
}

interface DraftMatchCloudSyncRepository {
    suspend fun sync(snapshot: DraftMatchCloudSyncSnapshot): DraftMatchCloudSyncResult
}

fun interface DraftMatchCloudSyncAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<DraftMatchCloudSyncResult>

    suspend operator fun invoke(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> =
        throw SecurityException("Expected owner is required for draft synchronization.")
}

fun interface DraftMatchCloudSyncRetryAction {
    suspend fun executeForRetry(tournamentId: String): DraftMatchCloudSyncResult
    suspend fun executeForRetry(tournamentId: String, expectedOwnerUserId: String): DraftMatchCloudSyncResult =
        throw SecurityException("Expected queue owner is required.")
}
