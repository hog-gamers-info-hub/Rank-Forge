package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

class SaveGroupRotationTeamSetupUseCase(
    private val repository: GroupRotationTeamSetupLocalRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationTeamSetupLocalRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        candidate: GroupRotationTeamSetupCandidate,
    ): SaveGroupRotationTeamSetupResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user
            ?.id
            ?.takeIf { it.isNotBlank() }
            ?: return SaveGroupRotationTeamSetupResult.AuthenticationRequired
        return when (val result = repository.saveGroupRotationTeamSetup(candidate, ownerUserId)) {
            GroupRotationTeamSetupLocalSaveResult.Saved -> SaveGroupRotationTeamSetupResult.Saved
            GroupRotationTeamSetupLocalSaveResult.TournamentNotFound ->
                SaveGroupRotationTeamSetupResult.TournamentNotFound
            GroupRotationTeamSetupLocalSaveResult.ProtectedHistory ->
                SaveGroupRotationTeamSetupResult.ProtectedHistory
            is GroupRotationTeamSetupLocalSaveResult.InvalidSetup ->
                SaveGroupRotationTeamSetupResult.InvalidSetup(result.issues)
        }
    }
}

