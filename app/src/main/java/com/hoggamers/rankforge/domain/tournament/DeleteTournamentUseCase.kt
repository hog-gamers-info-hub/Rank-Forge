package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

sealed interface DeleteTournamentResult {
    data object Success : DeleteTournamentResult
    data object TargetNotFound : DeleteTournamentResult
    data object AuthenticationRequired : DeleteTournamentResult
    data object PendingSyncPreparationFailed : DeleteTournamentResult
    data class StorageDeletionFailed(val category: CloudDeletionFailureCategory) : DeleteTournamentResult
    data class RemoteDeletionFailed(val category: CloudDeletionFailureCategory) : DeleteTournamentResult
    data object RemoteDeletedLocalCleanupFailed : DeleteTournamentResult
}

@Singleton
class DeleteTournamentUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val queueRepository: PersistentSyncQueueRepository,
    private val cloudDeletionRepository: CloudDeletionRepository,
    private val localDeletionRepository: LocalDeletionRepository,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
) {
    suspend operator fun invoke(tournamentId: String): DeleteTournamentResult {
        val ownerUserId = currentOwnerUserId() ?: return DeleteTournamentResult.AuthenticationRequired
        val existingIntent = try {
            deletionIntentRepository.read(DeletionTargetType.TOURNAMENT, tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.PendingSyncPreparationFailed
        }
        if (existingIntent != null && existingIntent.ownerUserId != ownerUserId) {
            return DeleteTournamentResult.AuthenticationRequired
        }
        if (existingIntent?.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING) {
            return completePendingLocalCleanup(tournamentId)
        }
        val tournament = try {
            tournamentRepository.observeById(tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.TargetNotFound
        }

        if (existingIntent != null && tournament == null) {
            deletionIntentRepository.clear(DeletionTargetType.TOURNAMENT, tournamentId)
            return DeleteTournamentResult.Success
        }
        if (tournament == null) return DeleteTournamentResult.TargetNotFound
        if (existingIntent == null) try {
            deletionIntentRepository.start(
                DeletionIntent(
                    targetType = DeletionTargetType.TOURNAMENT,
                    targetId = tournament.id,
                    tournamentId = tournament.id,
                    ownerUserId = ownerUserId,
                    phase = DeletionIntentPhase.DELETE_STARTED,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.PendingSyncPreparationFailed
        }
        if (!purgeQueue(tournament.id)) return DeleteTournamentResult.PendingSyncPreparationFailed
        val matchIds = try {
            tournamentRepository.observeMatchesByTournamentId(tournament.id).first().map { it.id }.toSet()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.TargetNotFound
        }

        when (val result = cloudDeletionRepository.deleteTournamentStorage(tournament.id, matchIds)) {
            CloudDeletionStageResult.Success -> Unit
            is CloudDeletionStageResult.Failed -> return DeleteTournamentResult.StorageDeletionFailed(result.category)
        }
        when (val result = cloudDeletionRepository.deleteTournamentRemote(tournament.id)) {
            CloudDeletionStageResult.Success -> Unit
            is CloudDeletionStageResult.Failed -> return DeleteTournamentResult.RemoteDeletionFailed(result.category)
        }
        try {
            deletionIntentRepository.markRemoteDeleted(DeletionTargetType.TOURNAMENT, tournament.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
        }
        return completePendingLocalCleanup(tournament.id)
    }

    /** Completes local cleanup after a prior RemoteDeletedLocalCleanupFailed result. */
    suspend fun retryLocalCleanup(tournamentId: String): DeleteTournamentResult {
        val ownerUserId = currentOwnerUserId() ?: return DeleteTournamentResult.AuthenticationRequired
        val intent = deletionIntentRepository.read(DeletionTargetType.TOURNAMENT, tournamentId)
        if (intent?.ownerUserId != ownerUserId ||
            intent.phase != DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING
        ) {
            return DeleteTournamentResult.RemoteDeletionFailed(CloudDeletionFailureCategory.AUTHORIZATION)
        }
        return completePendingLocalCleanup(tournamentId)
    }

    private suspend fun completePendingLocalCleanup(tournamentId: String): DeleteTournamentResult {
        val result = localResult(localDeletionRepository.deleteTournamentLocally(tournamentId), missingIsSuccess = true)
        if (result == DeleteTournamentResult.Success) {
            try {
                deletionIntentRepository.clear(DeletionTargetType.TOURNAMENT, tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
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

    private fun localResult(result: LocalDeletionResult, missingIsSuccess: Boolean = false): DeleteTournamentResult = when (result) {
        LocalDeletionResult.Deleted -> DeleteTournamentResult.Success
        LocalDeletionResult.NotFound -> if (missingIsSuccess) DeleteTournamentResult.Success else DeleteTournamentResult.TargetNotFound
        LocalDeletionResult.FileCleanupFailed -> DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
    }
}
