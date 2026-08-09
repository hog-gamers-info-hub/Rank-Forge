package com.hoggamers.rankforge.domain.auth

class SignInWithGoogleUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthOperationResult = repository.signInWithGoogle()
}
