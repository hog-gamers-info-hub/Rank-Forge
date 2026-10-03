package com.hoggamers.rankforge.domain.tournament

data class RosterPlayer(
    val tournamentId: String,
    val slotNumber: Int,
    val displayName: String,
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament id is required." }
        require(slotNumber in TeamSlot.TOURNAMENT_SLOT_NUMBERS) {
            "Team slot number must be between 1 and 24."
        }
    }

    companion object {
        const val MIN_PLAYERS = 0
        const val MAX_PLAYERS = 6

        fun create(
            tournamentId: String,
            slotNumber: Int,
            displayName: String,
        ): RosterPlayer = RosterPlayer(
            tournamentId = tournamentId,
            slotNumber = slotNumber,
            displayName = displayName,
        )
    }
}
