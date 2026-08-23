package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.first

sealed interface ConfirmTournamentRosterResult {
    data class Confirmed(
        val validation: RosterValidationResult,
    ) : ConfirmTournamentRosterResult

    data class AlreadyConfirmed(
        val validation: RosterValidationResult,
    ) : ConfirmTournamentRosterResult

    data class Invalid(
        val validation: RosterValidationResult,
    ) : ConfirmTournamentRosterResult

    data object NotFound : ConfirmTournamentRosterResult

    data object AuthenticationRequired : ConfirmTournamentRosterResult
}

class ConfirmTournamentRosterUseCase(
    private val repository: TournamentRepository,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
    private val authRepository: AuthRepository,
) {
    constructor(
        repository: TournamentRepository,
        validateTournamentRoster: ValidateTournamentRosterUseCase,
    ) : this(repository, validateTournamentRoster, SetupMutationUnauthenticatedAuthRepository)

    suspend operator fun invoke(tournamentId: String): ConfirmTournamentRosterResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return ConfirmTournamentRosterResult.AuthenticationRequired
        val tournament = repository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return ConfirmTournamentRosterResult.NotFound
        val validation = validateTournamentRoster(tournamentId)
        if (validation.issues.isNotEmpty()) {
            return ConfirmTournamentRosterResult.Invalid(validation)
        }
        if (tournament.status == TournamentStatus.CONFIRMED) {
            return ConfirmTournamentRosterResult.AlreadyConfirmed(validation)
        }
        return when (repository.confirmTournamentByOwner(tournamentId, ownerUserId)) {
            OwnerScopedTournamentConfirmationResult.Confirmed ->
                ConfirmTournamentRosterResult.Confirmed(validation)
            OwnerScopedTournamentConfirmationResult.AlreadyConfirmed ->
                ConfirmTournamentRosterResult.AlreadyConfirmed(validation)
            OwnerScopedTournamentConfirmationResult.TournamentNotFound ->
                ConfirmTournamentRosterResult.NotFound
        }
    }
}
