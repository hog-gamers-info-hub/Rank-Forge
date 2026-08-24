package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class ClearDraftMatchInput(
    val tournamentId: String,
    val matchId: String,
)

sealed interface ClearDraftMatchResult {
    data object Cleared : ClearDraftMatchResult
    data object AuthenticationRequired : ClearDraftMatchResult
    data object MatchNotFound : ClearDraftMatchResult
}

class ClearDraftMatchUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(input: ClearDraftMatchInput): ClearDraftMatchResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return ClearDraftMatchResult.AuthenticationRequired
        return when (repository.clearDraftMatchByOwner(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
            ownerUserId = ownerUserId,
        )) {
            OwnerScopedMatchMutationResult.Saved -> ClearDraftMatchResult.Cleared
            OwnerScopedMatchMutationResult.MatchNotFound -> ClearDraftMatchResult.MatchNotFound
        }
    }
}
