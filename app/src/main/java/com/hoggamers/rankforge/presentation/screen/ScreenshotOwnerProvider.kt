package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.ObserveAuthStateUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface ScreenshotOwnerProvider {
    suspend fun currentOwnerUserId(): String?
}

@Singleton
class AuthStateScreenshotOwnerProvider @Inject constructor(
    private val observeAuthState: ObserveAuthStateUseCase,
) : ScreenshotOwnerProvider {
    override suspend fun currentOwnerUserId(): String? =
        (observeAuthState().first() as? AuthState.SignedIn)?.user?.id?.takeIf { it.isNotBlank() }
}

class NoOpScreenshotOwnerProvider : ScreenshotOwnerProvider {
    override suspend fun currentOwnerUserId(): String? = "local-owner"
}
