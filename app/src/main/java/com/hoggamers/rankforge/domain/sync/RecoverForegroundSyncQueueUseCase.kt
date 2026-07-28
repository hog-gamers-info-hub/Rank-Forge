package com.hoggamers.rankforge.domain.sync

import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

fun interface ForegroundSyncQueueRecoveryAction {
    suspend fun recoverAfterAuthenticatedSession()
}

class RecoverForegroundSyncQueueUseCase @Inject constructor(
    private val queueRepository: PersistentSyncQueueRepository,
    private val retryCoordinator: ForegroundSyncQueueRetryCoordinator,
) : ForegroundSyncQueueRecoveryAction {
    override suspend fun recoverAfterAuthenticatedSession() {
        try {
            retryCoordinator.retryEligible(
                entries = queueRepository.observeAll().first(),
                hasAuthenticatedSession = true,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Queue recovery must not change the restored authentication result.
        }
    }
}
