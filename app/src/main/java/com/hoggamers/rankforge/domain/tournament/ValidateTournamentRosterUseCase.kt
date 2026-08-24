package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.first

class ValidateTournamentRosterUseCase(
    private val observeSlots: (String) -> kotlinx.coroutines.flow.Flow<List<TeamSlot>>,
    private val observePlayers: (String, Int) -> kotlinx.coroutines.flow.Flow<List<RosterPlayer>>,
    private val validator: RosterValidator,
) {
    constructor(
        repository: TournamentRepository,
        validator: RosterValidator,
    ) : this(
        observeSlots = repository::observeSlotsByTournamentId,
        observePlayers = repository::observeRosterByTournamentAndSlot,
        validator = validator,
    )

    constructor(
        observeTournamentSlots: ObserveTournamentSlotsUseCase,
        observeRosterPlayers: ObserveRosterPlayersUseCase,
        validator: RosterValidator,
    ) : this(
        observeSlots = observeTournamentSlots::invoke,
        observePlayers = observeRosterPlayers::invoke,
        validator = validator,
    )

    suspend operator fun invoke(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String> = emptyMap(),
        activeTeamSlotNumbers: Set<Int>? = null,
    ): RosterValidationResult {
        val slots = observeSlots(tournamentId)
            .first()
            .sortedBy { it.slotNumber }
            .let { allSlots ->
                if (activeTeamSlotNumbers == null) {
                    allSlots
                } else {
                    allSlots.filter { it.slotNumber in activeTeamSlotNumbers }
                }
            }
        val teams = slots.map { slot ->
            val players = observePlayers(tournamentId, slot.slotNumber).first()
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
