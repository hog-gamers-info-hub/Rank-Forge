package com.hoggamers.rankforge.domain.tournament

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
}

class ConfirmTournamentRosterUseCase(
    private val repository: TournamentRepository,
    private val validateTournamentRoster: ValidateTournamentRosterUseCase,
) {
    suspend operator fun invoke(tournamentId: String): ConfirmTournamentRosterResult {
        val tournament = repository.observeById(tournamentId).first()
            ?: return ConfirmTournamentRosterResult.NotFound
        val validation = validateTournamentRoster(tournamentId)
        if (validation.issues.isNotEmpty()) {
            return ConfirmTournamentRosterResult.Invalid(validation)
        }
        if (tournament.status == TournamentStatus.CONFIRMED) {
            return ConfirmTournamentRosterResult.AlreadyConfirmed(validation)
        }
        return if (repository.confirmTournament(tournamentId)) {
            ConfirmTournamentRosterResult.Confirmed(validation)
        } else {
            ConfirmTournamentRosterResult.AlreadyConfirmed(validation)
        }
    }
}
