package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface DeleteMatchResult {
    data object Success : DeleteMatchResult
    data object TargetNotFound : DeleteMatchResult
    data object AuthenticationRequired : DeleteMatchResult
    data object PendingSyncPreparationFailed : DeleteMatchResult
    data class StorageDeletionFailed(val category: CloudDeletionFailureCategory) : DeleteMatchResult
    data class RemoteDeletionFailed(val category: CloudDeletionFailureCategory) : DeleteMatchResult
    data object RemoteDeletedLocalCleanupFailed : DeleteMatchResult
}

@Singleton
class DeleteMatchUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val queueRepository: PersistentSyncQueueRepository,
    private val cloudDeletionRepository: CloudDeletionRepository,
    private val localDeletionRepository: LocalDeletionRepository,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) {
    suspend operator fun invoke(matchId: String): DeleteMatchResult {
        val ownerUserId = currentOwnerUserId() ?: return DeleteMatchResult.AuthenticationRequired
        val existingIntent = try {
            deletionIntentRepository.read(DeletionTargetType.MATCH, matchId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteMatchResult.PendingSyncPreparationFailed
        }
        if (existingIntent != null && existingIntent.ownerUserId != ownerUserId) {
            return DeleteMatchResult.AuthenticationRequired
        }
        if (existingIntent?.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING) {
            return completePendingLocalCleanup(matchId)
        }
        val match = try {
            tournamentRepository.observeMatchById(matchId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteMatchResult.TargetNotFound
        }

        if (existingIntent != null && match == null) {
            deletionIntentRepository.clear(DeletionTargetType.MATCH, matchId)
            return DeleteMatchResult.Success
        }
        if (match == null) return DeleteMatchResult.TargetNotFound
        if (existingIntent == null) try {
            deletionIntentRepository.start(
                DeletionIntent(
                    targetType = DeletionTargetType.MATCH,
                    targetId = match.id,
                    tournamentId = match.tournamentId,
                    ownerUserId = ownerUserId,
                    phase = DeletionIntentPhase.DELETE_STARTED,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteMatchResult.PendingSyncPreparationFailed
        }
        if (!purgeQueue(match.tournamentId)) return DeleteMatchResult.PendingSyncPreparationFailed

        when (val result = cloudDeletionRepository.deleteMatchStorage(match.tournamentId, match.id)) {
            CloudDeletionStageResult.Success -> Unit
            is CloudDeletionStageResult.Failed -> return DeleteMatchResult.StorageDeletionFailed(result.category)
        }
        when (val result = cloudDeletionRepository.deleteMatchRemote(match.tournamentId, match.id)) {
            CloudDeletionStageResult.Success -> Unit
            is CloudDeletionStageResult.Failed -> return DeleteMatchResult.RemoteDeletionFailed(result.category)
        }
        try {
            deletionIntentRepository.markRemoteDeleted(DeletionTargetType.MATCH, match.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteMatchResult.RemoteDeletedLocalCleanupFailed
        }
        return completePendingLocalCleanup(match.id)
    }

    /** Completes local cleanup after a prior RemoteDeletedLocalCleanupFailed result. */
    suspend fun retryLocalCleanup(matchId: String): DeleteMatchResult {
        val ownerUserId = currentOwnerUserId() ?: return DeleteMatchResult.AuthenticationRequired
        val intent = deletionIntentRepository.read(DeletionTargetType.MATCH, matchId)
        if (intent?.ownerUserId != ownerUserId ||
            intent.phase != DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
        ) {
            return DeleteMatchResult.RemoteDeletionFailed(CloudDeletionFailureCategory.AUTHORIZATION)
        }
        return completePendingLocalCleanup(matchId)
    }

    private suspend fun completePendingLocalCleanup(matchId: String): DeleteMatchResult {
        val result = localResult(localDeletionRepository.deleteMatchLocally(matchId), missingIsSuccess = true)
        if (result == DeleteMatchResult.Success) {
            try {
                deletionIntentRepository.clear(DeletionTargetType.MATCH, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return DeleteMatchResult.RemoteDeletedLocalCleanupFailed
            }
        }
        return result
    }

    private suspend fun currentOwnerUserId(): String? = try {
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun purgeQueue(tournamentId: String): Boolean = try {
        queueRepository.purgeByTournamentId(tournamentId)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private fun localResult(result: LocalDeletionResult, missingIsSuccess: Boolean = false): DeleteMatchResult = when (result) {
        LocalDeletionResult.Deleted -> DeleteMatchResult.Success
        LocalDeletionResult.NotFound -> if (missingIsSuccess) DeleteMatchResult.Success else DeleteMatchResult.TargetNotFound
        LocalDeletionResult.FileCleanupFailed -> DeleteMatchResult.RemoteDeletedLocalCleanupFailed
    }
}
