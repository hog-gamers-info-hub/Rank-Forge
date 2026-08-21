package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

enum class ConflictOperation { DRAFT_MATCH_SYNC, MATCH_RESTORATION }

enum class ConflictResolvability { DRAFT_RESOLVABLE, FINALIZED_OR_UNSUPPORTED }

/**
 * Explicit conflict data. Snapshots are present only when that side was safely available;
 * absent data is intentionally not inferred or merged.
 */
data class ConflictResolutionContext(
    val tournamentId: String,
    val matchId: String? = null,
    val operation: ConflictOperation,
    val conflict: RevisionConflict,
    val resolvability: ConflictResolvability,
    val localDraftMatches: List<Match> = emptyList(),
    val cloudDraftMatches: List<Match> = emptyList(),
    val localRevision: Int? = null,
    val baseCloudRevision: CloudRevision? = null,
    val currentCloudRevision: CloudRevision? = null,
)

sealed interface DraftConflictResolutionResult {
    data object KeepLocalSucceeded : DraftConflictResolutionResult
    data object AcceptedCloudDraft : DraftConflictResolutionResult
    data object Deferred : DraftConflictResolutionResult
    data class Conflict(val context: ConflictResolutionContext) : DraftConflictResolutionResult
    data object Unsupported : DraftConflictResolutionResult
    data object Failed : DraftConflictResolutionResult
}

interface DraftConflictResolver {
    suspend fun keepLocal(context: ConflictResolutionContext): DraftConflictResolutionResult
    suspend fun acceptCloudDraft(context: ConflictResolutionContext): DraftConflictResolutionResult
}

/** Foreground-only, user-initiated draft conflict actions. */
class ResolveDraftConflictUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val cloudRepository: MatchCloudRestorationRepository,
    private val localRepository: MatchRestorationLocalRepository,
    private val syncDraftMatches: DraftMatchCloudSyncAction,
    private val queueRepository: PersistentSyncQueueRepository,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) : DraftConflictResolver {
    override suspend fun keepLocal(context: ConflictResolutionContext): DraftConflictResolutionResult {
        if (context.resolvability != ConflictResolvability.DRAFT_RESOLVABLE) {
            return DraftConflictResolutionResult.Unsupported
        }
        if (deletionIntentRepository.isBlocking(context.tournamentId)) {
            return DraftConflictResolutionResult.Unsupported
        }
        val currentRevision = context.currentCloudRevision
            ?: return DraftConflictResolutionResult.Unsupported
        val localMatches = try {
            tournamentRepository.observeMatchesByTournamentId(context.tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DraftConflictResolutionResult.Failed
        }
        if (localMatches.isEmpty() || localMatches.any { it.status != MatchStatus.DRAFT }) {
            return DraftConflictResolutionResult.Unsupported
        }
        return try {
            tournamentRepository.rebaseCloudRevisionForConflictResolution(
                context.tournamentId,
                currentRevision.value,
            )
            when (val result = syncDraftMatches(context.tournamentId).primaryResult) {
                DraftMatchCloudSyncResult.Success -> DraftConflictResolutionResult.KeepLocalSucceeded
                is DraftMatchCloudSyncResult.Conflict -> DraftConflictResolutionResult.Conflict(
                    result.context ?: context.copy(conflict = result.conflict),
                )
                else -> DraftConflictResolutionResult.Failed
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            DraftConflictResolutionResult.Failed
        }
    }

    override suspend fun acceptCloudDraft(context: ConflictResolutionContext): DraftConflictResolutionResult {
        if (context.resolvability != ConflictResolvability.DRAFT_RESOLVABLE) {
            return DraftConflictResolutionResult.Unsupported
        }
        if (deletionIntentRepository.isBlocking(context.tournamentId)) {
            return DraftConflictResolutionResult.Unsupported
        }
        val localMatches = try {
            tournamentRepository.observeMatchesByTournamentId(context.tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DraftConflictResolutionResult.Failed
        }
        if (localMatches.any { it.status != MatchStatus.DRAFT }) return DraftConflictResolutionResult.Unsupported
        return try {
            when (val cloud = cloudRepository.readOwnedMatches(context.tournamentId)) {
                is MatchCloudRestorationRemoteResult.Failure -> DraftConflictResolutionResult.Failed
                is MatchCloudRestorationRemoteResult.Success -> {
                    val revision = cloud.value.cloudRevision ?: return DraftConflictResolutionResult.Unsupported
                    if (cloud.value.matches.any { it.status != MatchStatus.DRAFT }) {
                        return DraftConflictResolutionResult.Unsupported
                    }
                    localRepository.replaceDraftMatches(cloud.value)
                    queueRepository.completeOldestUnresolved(
                        SyncQueueOperationType.DRAFT_MATCH_SYNC,
                        context.tournamentId,
                    )
                    DraftConflictResolutionResult.AcceptedCloudDraft
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            DraftConflictResolutionResult.Failed
        }
    }
}
