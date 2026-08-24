package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

fun interface ForegroundSyncQueueRecoveryAction {
    suspend fun recoverAfterAuthenticatedSession()
}

class RecoverForegroundSyncQueueUseCase @Inject constructor(
    private val queueRepository: PersistentSyncQueueRepository,
    private val retryCoordinator: ForegroundSyncQueueRetryCoordinator,
    private val authRepository: AuthRepository,
) : ForegroundSyncQueueRecoveryAction {
    override suspend fun recoverAfterAuthenticatedSession() {
        try {
            val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
                ?.user?.id?.takeIf { it.isNotBlank() }
                ?: return
            retryCoordinator.retryEligible(
                entries = queueRepository.observePendingByOwner(ownerUserId).first(),
                ownerUserId = ownerUserId,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Queue recovery must not change the restored authentication result.
        }
    }
}
