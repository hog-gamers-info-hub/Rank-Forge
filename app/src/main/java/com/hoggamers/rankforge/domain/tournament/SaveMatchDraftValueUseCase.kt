package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class SaveMatchDraftValueInput(
    val tournamentId: String,
    val matchId: String,
    val teamSlotNumber: Int,
    val placementInput: String? = null,
    val killsInput: String? = null,
)

sealed interface SaveMatchDraftValueResult {
    data object Saved : SaveMatchDraftValueResult
    data object AuthenticationRequired : SaveMatchDraftValueResult
    data object MatchNotFound : SaveMatchDraftValueResult
}

class SaveMatchDraftValueUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(input: SaveMatchDraftValueInput): SaveMatchDraftValueResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return SaveMatchDraftValueResult.AuthenticationRequired
        return when (repository.saveDraftMatchValueByOwner(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
            ownerUserId = ownerUserId,
            teamSlotNumber = input.teamSlotNumber,
            placementInput = input.placementInput,
            killsInput = input.killsInput,
        )) {
            OwnerScopedMatchMutationResult.Saved -> SaveMatchDraftValueResult.Saved
            OwnerScopedMatchMutationResult.MatchNotFound -> SaveMatchDraftValueResult.MatchNotFound
        }
    }
}
