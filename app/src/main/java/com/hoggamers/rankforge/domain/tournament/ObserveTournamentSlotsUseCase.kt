package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveTournamentSlotsUseCase(
    private val observeSlots: (String) -> Flow<List<TeamSlot>>,
) {
    constructor(repository: TournamentRepository) : this(repository::observeSlotsByTournamentId)

    constructor(
        repository: TournamentRepository,
        authRepository: AuthRepository,
    ) : this(
        observeSlots = { tournamentId ->
            authRepository.observeAuthState().flatMapLatest { authState ->
                val ownerUserId = (authState as? AuthState.SignedIn)?.user?.id
                    ?.takeIf { it.isNotBlank() }
                if (ownerUserId == null) {
                    flowOf(emptyList())
                } else {
                    repository.observeSlotsByTournamentIdAndOwner(tournamentId, ownerUserId)
                }
            }
        },
    )

    operator fun invoke(tournamentId: String): Flow<List<TeamSlot>> =
        observeSlots(tournamentId)
            .map { slots -> slots.sortedBy { it.slotNumber } }
}
