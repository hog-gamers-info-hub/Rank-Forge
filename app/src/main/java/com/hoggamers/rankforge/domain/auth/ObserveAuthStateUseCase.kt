package com.hoggamers.rankforge.domain.auth

class ObserveAuthStateUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke() = repository.observeAuthState()
}
