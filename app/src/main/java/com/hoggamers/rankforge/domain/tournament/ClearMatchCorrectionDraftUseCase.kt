package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

data class ClearMatchCorrectionDraftInput(
    val tournamentId: String,
    val matchId: String,
)

sealed interface ClearMatchCorrectionDraftResult {
    data object Cleared : ClearMatchCorrectionDraftResult
    data object AuthenticationRequired : ClearMatchCorrectionDraftResult
    data object MatchNotFound : ClearMatchCorrectionDraftResult
}

class ClearMatchCorrectionDraftUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: TournamentRepository) : this(repository, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(input: ClearMatchCorrectionDraftInput): ClearMatchCorrectionDraftResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return ClearMatchCorrectionDraftResult.AuthenticationRequired
        return when (
            repository.clearMatchCorrectionDraftByOwner(input.tournamentId, input.matchId, ownerUserId)
        ) {
            OwnerScopedMatchMutationResult.Saved -> ClearMatchCorrectionDraftResult.Cleared
            OwnerScopedMatchMutationResult.MatchNotFound -> ClearMatchCorrectionDraftResult.MatchNotFound
        }
    }
}
