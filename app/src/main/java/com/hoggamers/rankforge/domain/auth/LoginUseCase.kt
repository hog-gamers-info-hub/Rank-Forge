package com.hoggamers.rankforge.domain.auth

class LoginUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): AuthOperationResult = repository.login(email, password)
}
