package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

sealed interface SaveRosterResult {
    data object Saved : SaveRosterResult

    data object AuthenticationRequired : SaveRosterResult

    data object TournamentNotFound : SaveRosterResult
}

class SaveRosterUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ): SaveRosterResult {
        require(players.size <= RosterPlayer.MAX_PLAYERS) {
            "A team roster cannot contain more than six players."
        }
        require(players.all { player ->
            player.tournamentId == tournamentId && player.slotNumber == slotNumber
        }) {
            "Roster players must belong to the requested tournament and team slot."
        }
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return SaveRosterResult.AuthenticationRequired
        return when (repository.saveRosterByOwner(tournamentId, ownerUserId, slotNumber, players)) {
            OwnerScopedTournamentMutationResult.Saved -> SaveRosterResult.Saved
            OwnerScopedTournamentMutationResult.TournamentNotFound -> SaveRosterResult.TournamentNotFound
        }
    }
}
