package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ReadGroupRotationTeamSetupUseCase @Inject constructor(
    private val repository: GroupRotationTeamSetupReadRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(tournamentId: String): ReadGroupRotationTeamSetupResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: return ReadGroupRotationTeamSetupResult.AuthenticationRequired
        return when (val result = repository.readGroupRotationTeamSetup(tournamentId, ownerUserId)) {
            is GroupRotationTeamSetupReadResult.Loaded ->
                ReadGroupRotationTeamSetupResult.Loaded(result.setup.tournament, result.setup.candidate)
            is GroupRotationTeamSetupReadResult.NoSavedSetup ->
                ReadGroupRotationTeamSetupResult.NoSavedSetup(result.tournament)
            GroupRotationTeamSetupReadResult.TournamentNotFound ->
                ReadGroupRotationTeamSetupResult.TournamentNotFound
            GroupRotationTeamSetupReadResult.InvalidStoredSetup ->
                ReadGroupRotationTeamSetupResult.InvalidStoredSetup
        }
    }
}

sealed interface ReadGroupRotationTeamSetupResult {
    data class Loaded(
        val tournament: Tournament,
        val candidate: GroupRotationTeamSetupCandidate,
    ) : ReadGroupRotationTeamSetupResult

    data class NoSavedSetup(val tournament: Tournament) : ReadGroupRotationTeamSetupResult

    data object AuthenticationRequired : ReadGroupRotationTeamSetupResult

    data object TournamentNotFound : ReadGroupRotationTeamSetupResult

    data object InvalidStoredSetup : ReadGroupRotationTeamSetupResult
}
