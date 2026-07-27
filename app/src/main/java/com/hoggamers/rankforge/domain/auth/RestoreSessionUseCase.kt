package com.hoggamers.rankforge.domain.auth

class RestoreSessionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthRestorationResult = repository.restoreSession()
}
