package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ReadGroupRotationTeamSetupUseCase @Inject constructor(
    private val repository: GroupRotationTeamSetupReadRepository,
    private val authRepository: AuthRepository,
    private val identityRepository: GroupRotationTeamIdentityLocalRepository,
) {
    suspend operator fun invoke(tournamentId: String): ReadGroupRotationTeamSetupResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: return ReadGroupRotationTeamSetupResult.AuthenticationRequired
        return when (val result = repository.readGroupRotationTeamSetup(tournamentId, ownerUserId)) {
            is GroupRotationTeamSetupReadResult.Loaded ->
                readIdentityAuthoritative(result.setup.tournament, result.setup.candidate)
            is GroupRotationTeamSetupReadResult.NoSavedSetup ->
                readIdentityAuthoritative(result.tournament, null)
            GroupRotationTeamSetupReadResult.TournamentNotFound ->
                ReadGroupRotationTeamSetupResult.TournamentNotFound
            GroupRotationTeamSetupReadResult.InvalidStoredSetup ->
                ReadGroupRotationTeamSetupResult.InvalidStoredSetup
        }
    }

    private suspend fun readIdentityAuthoritative(
        tournament: Tournament,
        fallback: GroupRotationTeamSetupCandidate?,
    ): ReadGroupRotationTeamSetupResult {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return if (fallback == null) {
                ReadGroupRotationTeamSetupResult.NoSavedSetup(tournament)
            } else {
                ReadGroupRotationTeamSetupResult.Loaded(
                    tournament = tournament,
                    candidate = fallback,
                    identityAuthoritative = false,
                )
            }
        }
        return when (val result = identityRepository.read(tournament)) {
            is GroupRotationTeamIdentityReadResult.Loaded ->
                ReadGroupRotationTeamSetupResult.Loaded(
                    tournament = tournament,
                    candidate = result.snapshot.toSetupCandidate(tournament),
                    identityAuthoritative = true,
                )
            is GroupRotationTeamIdentityReadResult.Invalid ->
                ReadGroupRotationTeamSetupResult.InvalidStoredSetup
            GroupRotationTeamIdentityReadResult.Unsupported ->
                if (fallback == null) {
                    ReadGroupRotationTeamSetupResult.NoSavedSetup(tournament)
                } else {
                    ReadGroupRotationTeamSetupResult.Loaded(
                        tournament = tournament,
                        candidate = fallback,
                        identityAuthoritative = false,
                    )
                }
        }
    }
}

sealed interface ReadGroupRotationTeamSetupResult {
    data class Loaded(
        val tournament: Tournament,
        val candidate: GroupRotationTeamSetupCandidate,
        val identityAuthoritative: Boolean = false,
    ) : ReadGroupRotationTeamSetupResult

    data class NoSavedSetup(val tournament: Tournament) : ReadGroupRotationTeamSetupResult

    data object AuthenticationRequired : ReadGroupRotationTeamSetupResult

    data object TournamentNotFound : ReadGroupRotationTeamSetupResult

    data object InvalidStoredSetup : ReadGroupRotationTeamSetupResult
}
