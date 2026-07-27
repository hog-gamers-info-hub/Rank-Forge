package com.hoggamers.rankforge.domain.auth

class RestoreSessionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthOperationResult = repository.restoreSession()
}
