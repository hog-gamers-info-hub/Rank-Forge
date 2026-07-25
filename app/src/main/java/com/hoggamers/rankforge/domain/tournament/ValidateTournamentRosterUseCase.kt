package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.first

class ValidateTournamentRosterUseCase(
    private val repository: TournamentRepository,
    private val validator: RosterValidator,
) {
    suspend operator fun invoke(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String> = emptyMap(),
    ): RosterValidationResult {
        val slots = repository.observeSlotsByTournamentId(tournamentId).first().sortedBy { it.slotNumber }
        val teams = slots.map { slot ->
            val players = repository
                .observeRosterByTournamentAndSlot(tournamentId, slot.slotNumber)
                .first()
            RosterValidationTeam(
                slotNumber = slot.slotNumber,
                teamName = teamNamesBySlotNumber[slot.slotNumber] ?: slot.teamName,
                players = players.mapIndexed { index, player ->
                    RosterValidationPlayer(
                        playerIndex = index,
                        displayName = player.displayName,
                    )
                },
            )
        }
        return validator.validate(teams)
    }
}
