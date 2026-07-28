package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RestoreTournamentUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val cloudRepository: TournamentCloudRestorationRepository,
    private val localRepository: TournamentRestorationLocalRepository,
) : TournamentCloudRestorationAction {
    override suspend fun loadAvailable(): TournamentCloudRestorationResult {
        if (!isAuthenticated()) return TournamentCloudRestorationResult.AuthenticationRequired
        return cloudRepository.listOwnedTournaments().toResult()
    }

    override suspend fun restore(tournamentId: String): TournamentCloudRestorationResult {
        if (!isAuthenticated()) return TournamentCloudRestorationResult.AuthenticationRequired
        return when (val result = cloudRepository.readOwnedTournament(tournamentId)) {
            is TournamentCloudRestorationRemoteResult.Failure -> result.toDomainResult()
            is TournamentCloudRestorationRemoteResult.Success -> {
                try {
                    localRepository.restore(result.value)
                    TournamentCloudRestorationResult.Success(result.value.tournament.name)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    TournamentCloudRestorationResult.LocalTransactionFailure
                }
            }
        }
    }

    private suspend fun isAuthenticated(): Boolean = try {
        authRepository.observeAuthState().first() is AuthState.SignedIn
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}

private fun TournamentCloudRestorationRemoteResult.Failure.toDomainResult(): TournamentCloudRestorationResult = when (category) {
    TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
        TournamentCloudRestorationResult.AuthenticationRequired
    TournamentCloudRestorationFailureCategory.AUTHORIZATION ->
        TournamentCloudRestorationResult.AuthorizationFailure
    TournamentCloudRestorationFailureCategory.NETWORK ->
        TournamentCloudRestorationResult.NetworkFailure
    TournamentCloudRestorationFailureCategory.VALIDATION ->
        TournamentCloudRestorationResult.ValidationFailure
}

private fun TournamentCloudRestorationRemoteResult<List<TournamentCloudRestorationSummary>>.toResult(): TournamentCloudRestorationResult = when (this) {
    is TournamentCloudRestorationRemoteResult.Success ->
        TournamentCloudRestorationResult.Available(value)
    is TournamentCloudRestorationRemoteResult.Failure -> toDomainResult()
}
