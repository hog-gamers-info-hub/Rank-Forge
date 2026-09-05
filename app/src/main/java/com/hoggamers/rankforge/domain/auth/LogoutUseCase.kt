package com.hoggamers.rankforge.domain.auth

class LogoutUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthOperationResult = repository.logout()

    suspend fun clearLocalSession() = repository.clearLocalSession()
}
