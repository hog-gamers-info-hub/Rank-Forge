package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class GetTournamentByIdUseCase(
    private val observeTournament: (String) -> Flow<Tournament?>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeById)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeTournament = { tournamentId ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(null)
                } else {
                    repository.observeByIdAndOwner(tournamentId, ownerUserId)
                }
            }
        },
    )

    operator fun invoke(tournamentId: String): Flow<Tournament?> =
        observeTournament(tournamentId)
}
