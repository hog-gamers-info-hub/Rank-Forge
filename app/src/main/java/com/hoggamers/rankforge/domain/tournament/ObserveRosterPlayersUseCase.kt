package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRosterPlayersUseCase(
    private val observePlayers: (String, Int) -> Flow<List<RosterPlayer>>,
) {
    constructor(repository: TournamentRepository) : this(
        repository::observeRosterByTournamentAndSlot,
    )

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observePlayers = { tournamentId, slotNumber ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(emptyList())
                } else {
                    repository.observeRosterByTournamentAndSlotAndOwner(
                        tournamentId,
                        slotNumber,
                        ownerUserId,
                    )
                }
            }
        },
    )

    operator fun invoke(
        tournamentId: String,
        slotNumber: Int,
    ): Flow<List<RosterPlayer>> = observePlayers(tournamentId, slotNumber)
}
