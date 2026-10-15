package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

class ReadMatchTeamIdentityContextUseCase(
    private val repository: MatchTeamIdentityContextRepository = NoOpMatchTeamIdentityContextRepository,
    private val authRepository: AuthRepository = SetupMutationUnauthenticatedAuthRepository,
) {
    suspend fun forMatch(matchId: String): MatchTeamIdentityContextReadResult =
        withOwner { ownerUserId -> repository.readForMatch(matchId, ownerUserId) }

    suspend fun forPairing(
        tournamentId: String,
        pairing: GroupPairing,
    ): MatchTeamIdentityContextReadResult =
        withOwner { ownerUserId -> repository.readForPairing(tournamentId, pairing, ownerUserId) }

    private suspend fun withOwner(
        block: suspend (String) -> MatchTeamIdentityContextReadResult,
    ): MatchTeamIdentityContextReadResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
        ?.user?.id?.takeIf { it.isNotBlank() }
        return if (ownerUserId == null) {
            MatchTeamIdentityContextReadResult.AuthenticationRequired
        } else {
            block(ownerUserId)
        }
    }
}

internal object NoOpMatchTeamIdentityContextRepository : MatchTeamIdentityContextRepository {
    override suspend fun readForMatch(
        matchId: String,
        ownerUserId: String,
    ): MatchTeamIdentityContextReadResult = MatchTeamIdentityContextReadResult.InvalidMapping

    override suspend fun readForPairing(
        tournamentId: String,
        pairing: GroupPairing,
        ownerUserId: String,
    ): MatchTeamIdentityContextReadResult = MatchTeamIdentityContextReadResult.InvalidMapping
}
