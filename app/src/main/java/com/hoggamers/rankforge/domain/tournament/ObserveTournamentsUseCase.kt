package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class ObserveTournamentsUseCase(
    private val observeTournaments: () -> Flow<List<Tournament>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeAll)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeTournaments = {
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(emptyList())
                } else {
                    repository.observeAllByOwner(ownerUserId)
                }
            }
        },
    )

    operator fun invoke(): Flow<List<Tournament>> =
        observeTournaments()
}
