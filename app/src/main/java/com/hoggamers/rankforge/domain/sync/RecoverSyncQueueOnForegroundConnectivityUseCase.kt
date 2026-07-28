package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

fun interface ForegroundConnectivityRetryAction {
    suspend fun onConnectivityChanged(isNetworkAvailable: Boolean)
}

class RecoverSyncQueueOnForegroundConnectivityUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val queueRecovery: ForegroundSyncQueueRecoveryAction,
) : ForegroundConnectivityRetryAction {
    override suspend fun onConnectivityChanged(isNetworkAvailable: Boolean) {
        if (!isNetworkAvailable) return
        try {
            val isSignedIn = authRepository.observeAuthState().first() is AuthState.SignedIn
            if (isSignedIn) {
                queueRecovery.recoverAfterAuthenticatedSession()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Connectivity-triggered recovery must not affect the foreground app flow.
        }
    }
}
