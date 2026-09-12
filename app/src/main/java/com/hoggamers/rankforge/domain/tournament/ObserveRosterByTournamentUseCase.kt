package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRosterByTournamentUseCase(
    private val observeRoster: (String) -> Flow<Map<Int, List<RosterPlayer>>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeRosterByTournamentId)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeRoster = { tournamentId ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                when (authState) {
                    AuthState.Loading -> emptyFlow()

                    is AuthState.SignedIn -> {
                        val ownerUserId = authState.user.id.takeIf { it.isNotBlank() }
                        if (ownerUserId == null) {
                            flowOf(emptyMap())
                        } else {
                            repository.observeRosterByTournamentIdAndOwner(tournamentId, ownerUserId)
                        }
                    }

                    else -> flowOf(emptyMap())
                }
            }
        },
    )

    operator fun invoke(tournamentId: String): Flow<Map<Int, List<RosterPlayer>>> =
        observeRoster(tournamentId)
}
