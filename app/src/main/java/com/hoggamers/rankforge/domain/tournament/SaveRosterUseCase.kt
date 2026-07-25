package com.hoggamers.rankforge.domain.tournament

class SaveRosterUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(
        tournamentId: String,
        slotNumber: Int,
        players: List<RosterPlayer>,
    ) {
        require(players.size <= RosterPlayer.MAX_PLAYERS) {
            "A team roster cannot contain more than six players."
        }
        require(players.all { player ->
            player.tournamentId == tournamentId && player.slotNumber == slotNumber
        }) {
            "Roster players must belong to the requested tournament and team slot."
        }
        repository.saveRoster(
            tournamentId = tournamentId,
            slotNumber = slotNumber,
            players = players,
        )
    }
}
