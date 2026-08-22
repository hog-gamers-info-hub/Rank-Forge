package com.hoggamers.rankforge.domain.auth

class UpdateRecoveredPasswordUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(newPassword: String): AuthOperationResult =
        repository.updateRecoveredPassword(newPassword)
}
