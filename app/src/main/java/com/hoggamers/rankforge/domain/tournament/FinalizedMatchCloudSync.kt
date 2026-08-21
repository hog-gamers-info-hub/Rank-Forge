package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class FinalizedMatchCloudSyncSnapshot(
    val tournament: Tournament,
    val teamSlots: List<TeamSlot>,
    val matches: List<Match>,
    val expectedCloudRevision: Int? = null,
)

enum class FinalizedMatchCloudSyncStage {
    MATCHES,
}

sealed interface FinalizedMatchCloudSyncResult {
    data class Success(
        val confirmedCloudRevision: Int,
    ) : FinalizedMatchCloudSyncResult {
        init {
            require(confirmedCloudRevision > 0) { "Confirmed cloud revisions must be positive." }
        }
    }
    data object AuthenticationRequired : FinalizedMatchCloudSyncResult
    data object ValidationFailure : FinalizedMatchCloudSyncResult
    data object AuthorizationFailure : FinalizedMatchCloudSyncResult
    data object NetworkFailure : FinalizedMatchCloudSyncResult
    data class Conflict(
        val conflict: RevisionConflict,
        val confirmedCloudRevision: Int? = null,
    ) : FinalizedMatchCloudSyncResult
    data class PartialFailure(
        val completedStage: FinalizedMatchCloudSyncStage,
        val confirmedCloudRevision: Int? = null,
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
