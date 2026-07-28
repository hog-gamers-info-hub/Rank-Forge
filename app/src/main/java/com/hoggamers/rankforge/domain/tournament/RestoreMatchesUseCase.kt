package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RestoreMatchesUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: MatchCloudRestorationRepository,
    private val localRepository: MatchRestorationLocalRepository,
) : MatchCloudRestorationAction {
    override suspend fun invoke(tournamentId: String): MatchCloudRestorationResult {
        if (!isAuthenticated()) return MatchCloudRestorationResult.AuthenticationRequired
        return when (val result = cloudRepository.readOwnedMatches(tournamentId)) {
            is MatchCloudRestorationRemoteResult.Failure -> result.toDomainResult()
            is MatchCloudRestorationRemoteResult.Success -> {
                if (result.value.matches.isEmpty()) return MatchCloudRestorationResult.NoCloudMatches
                try {
                    localRepository.replaceMatches(result.value)
                    MatchCloudRestorationResult.Success
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    MatchCloudRestorationResult.LocalTransactionFailure
                }
            }
        }
    }

    private suspend fun isAuthenticated() = try {
        authRepository.observeAuthState().first() is AuthState.SignedIn
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) { false }
}

private fun MatchCloudRestorationRemoteResult.Failure.toDomainResult() = when (category) {
    MatchCloudRestorationFailureCategory.AUTHENTICATION -> MatchCloudRestorationResult.AuthenticationRequired
    MatchCloudRestorationFailureCategory.AUTHORIZATION -> MatchCloudRestorationResult.AuthorizationFailure
    MatchCloudRestorationFailureCategory.NETWORK -> MatchCloudRestorationResult.NetworkFailure
    MatchCloudRestorationFailureCategory.VALIDATION -> MatchCloudRestorationResult.ValidationFailure
}
