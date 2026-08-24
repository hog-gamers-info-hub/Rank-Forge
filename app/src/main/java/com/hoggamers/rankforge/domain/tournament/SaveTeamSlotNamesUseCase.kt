package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

sealed interface SaveTeamSlotNamesResult {
    data object Saved : SaveTeamSlotNamesResult

    data object AuthenticationRequired : SaveTeamSlotNamesResult

    data object TournamentNotFound : SaveTeamSlotNamesResult
}

class SaveTeamSlotNamesUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ): SaveTeamSlotNamesResult {
        val normalizedNames = teamNamesBySlotNumber.mapValues { (_, teamName) -> teamName.trim() }
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return SaveTeamSlotNamesResult.AuthenticationRequired
        return when (repository.saveTeamNamesByOwner(tournamentId, ownerUserId, normalizedNames)) {
            OwnerScopedTournamentMutationResult.Saved -> SaveTeamSlotNamesResult.Saved
            OwnerScopedTournamentMutationResult.TournamentNotFound -> SaveTeamSlotNamesResult.TournamentNotFound
        }
    }
}
