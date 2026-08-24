package com.hoggamers.rankforge.domain.auth

import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.first

internal suspend fun AuthRepository.isSignedInAs(expectedOwnerUserId: String): Boolean {
    if (expectedOwnerUserId.isBlank()) return false
    return try {
        (observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id == expectedOwnerUserId
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}
