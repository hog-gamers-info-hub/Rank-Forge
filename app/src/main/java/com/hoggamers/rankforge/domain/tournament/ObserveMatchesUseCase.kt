package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatchesUseCase(
    private val observeMatches: (String) -> Flow<List<Match>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeMatchesByTournamentId)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeMatches = { tournamentId ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                when (authState) {
                    AuthState.Loading -> emptyFlow()
                    is AuthState.SignedIn -> {
                        if (ownerUserId == null) {
                            flowOf(emptyList())
                        } else {
                            repository.observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId)
                        }
                    }
                    AuthState.SignedOut,
                    is AuthState.RestorationWarning,
                    is AuthState.SessionExpired,
                    is AuthState.Error,
                    -> flowOf(emptyList())
                }
            }
        },
    )

    operator fun invoke(tournamentId: String): Flow<List<Match>> =
        observeMatches(tournamentId)
}
