package com.hoggamers.rankforge.domain.auth

class SignUpUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): AuthOperationResult = repository.signUp(email, password)
}
