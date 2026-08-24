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
        val tournament = try {
            tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.TargetNotFound
        }
        val existingIntent = try {
            deletionIntentRepository.findByTargetAndOwner(
                DeletionTargetType.TOURNAMENT,
                tournamentId,
                ownerUserId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.PendingSyncPreparationFailed
        }
        if (existingIntent?.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING) {
            return completePendingLocalCleanup(tournamentId, ownerUserId)
        }
        if (existingIntent != null && tournament == null) {
            deletionIntentRepository.clearByTargetAndOwner(
                DeletionTargetType.TOURNAMENT,
                tournamentId,
                ownerUserId,
            )
            return DeleteTournamentResult.Success
        }
        if (tournament == null) return DeleteTournamentResult.TargetNotFound
        if (existingIntent == null) {
            val started = try {
                deletionIntentRepository.startIfAbsent(
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
            if (!started) {
                val reread = try {
                    deletionIntentRepository.findByTargetAndOwner(
                        DeletionTargetType.TOURNAMENT,
                        tournamentId,
                        ownerUserId,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return DeleteTournamentResult.PendingSyncPreparationFailed
                }
                if (reread == null) return DeleteTournamentResult.TargetNotFound
                if (reread.phase == DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING) {
                    return completePendingLocalCleanup(tournamentId, ownerUserId)
                }
            }
        }
        if (!purgeQueue(tournament.id, ownerUserId)) return DeleteTournamentResult.PendingSyncPreparationFailed
        val matchIds = try {
            tournamentRepository.observeMatchesByTournamentIdAndOwner(tournament.id, ownerUserId)
                .first().map { it.id }.toSet()
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
            if (!deletionIntentRepository.markRemoteDeletedByTargetAndOwner(
                    DeletionTargetType.TOURNAMENT,
                    tournament.id,
                    ownerUserId,
                )
            ) return DeleteTournamentResult.TargetNotFound
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
        }
        return completePendingLocalCleanup(tournament.id, ownerUserId)
    }

    /** Completes local cleanup after a prior RemoteDeletedLocalCleanupFailed result. */
    suspend fun retryLocalCleanup(tournamentId: String): DeleteTournamentResult {
        val ownerUserId = currentOwnerUserId() ?: return DeleteTournamentResult.AuthenticationRequired
        val intent = deletionIntentRepository.findByTargetAndOwner(
            DeletionTargetType.TOURNAMENT,
            tournamentId,
            ownerUserId,
        ) ?: return DeleteTournamentResult.TargetNotFound
        if (intent.phase != DeletionIntentPhase.REMOTE_DELETED_LOCAL_CLEANUP_PENDING) {
            return DeleteTournamentResult.TargetNotFound
        }
        return completePendingLocalCleanup(tournamentId, ownerUserId)
    }

    private suspend fun completePendingLocalCleanup(
        tournamentId: String,
        ownerUserId: String,
    ): DeleteTournamentResult {
        val result = localResult(
            localDeletionRepository.deleteTournamentLocallyByOwner(tournamentId, ownerUserId),
            missingIsSuccess = true,
        )
        if (result == DeleteTournamentResult.Success) {
            try {
                if (!deletionIntentRepository.clearByTargetAndOwner(
                        DeletionTargetType.TOURNAMENT,
                        tournamentId,
                        ownerUserId,
                    )
                ) return DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
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

    private suspend fun purgeQueue(tournamentId: String, ownerUserId: String): Boolean = try {
        queueRepository.purgeByTournamentIdAndOwner(tournamentId, ownerUserId)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

    private fun localResult(result: LocalDeletionResult, missingIsSuccess: Boolean = false): DeleteTournamentResult = when (result) {
        LocalDeletionResult.Deleted -> DeleteTournamentResult.Success
        LocalDeletionResult.NotFound -> if (missingIsSuccess) DeleteTournamentResult.Success else DeleteTournamentResult.TargetNotFound
        LocalDeletionResult.CleanupClaimLost -> DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
        LocalDeletionResult.FileCleanupFailed -> DeleteTournamentResult.RemoteDeletedLocalCleanupFailed
    }
}
