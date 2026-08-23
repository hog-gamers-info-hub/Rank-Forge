package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ObserveTournamentSummariesUseCase(
    private val observeSummaries: () -> Flow<List<TournamentSummary>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeSummaries)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeSummaries = {
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(emptyList())
                } else {
                    repository.observeSummariesByOwner(ownerUserId)
                }
            }
        },
    )

    constructor(observeTournaments: ObserveTournamentsUseCase) : this(
        observeSummaries = {
            observeTournaments().map { tournaments ->
                tournaments.map { tournament ->
                    TournamentSummary(tournament, 0, 0, null)
                }
            }
        },
    )

    operator fun invoke(): Flow<List<TournamentSummary>> = observeSummaries()
}
