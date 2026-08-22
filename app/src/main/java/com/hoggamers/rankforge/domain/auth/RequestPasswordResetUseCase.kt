package com.hoggamers.rankforge.domain.auth

class RequestPasswordResetUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(email: String): AuthOperationResult =
        repository.requestPasswordReset(email)
}
