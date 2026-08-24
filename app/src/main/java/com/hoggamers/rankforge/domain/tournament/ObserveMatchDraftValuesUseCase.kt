package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatchDraftValuesUseCase(
    private val observeDraftValues: (String, String) -> Flow<Map<Int, MatchDraftFieldValues>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeDraftMatchValues)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeDraftValues = { tournamentId, matchId ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(emptyMap())
                } else {
                    repository.observeDraftMatchValuesByOwner(tournamentId, matchId, ownerUserId)
                }
            }
        },
    )

    operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): Flow<Map<Int, MatchDraftFieldValues>> = observeDraftValues(tournamentId, matchId)
}
