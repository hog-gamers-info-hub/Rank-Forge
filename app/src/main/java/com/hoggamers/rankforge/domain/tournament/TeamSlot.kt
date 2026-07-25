package com.hoggamers.rankforge.domain.tournament

data class TeamSlot(
    val tournamentId: String,
    val slotNumber: Int,
    val teamName: String = "",
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament id is required." }
        require(slotNumber in SLOT_NUMBERS) { "Team slot number must be between 1 and 12." }
    }

    companion object {
        const val MIN_SLOT_NUMBER = 1
        const val MAX_SLOT_NUMBER = 12
        val SLOT_NUMBERS: IntRange = MIN_SLOT_NUMBER..MAX_SLOT_NUMBER

        fun create(
            tournamentId: String,
            slotNumber: Int,
            teamName: String = "",
        ): TeamSlot =
            TeamSlot(
                tournamentId = tournamentId,
                slotNumber = slotNumber,
                teamName = teamName,
            )

        fun fixedSlotsForTournament(tournamentId: String): List<TeamSlot> =
            SLOT_NUMBERS.map { slotNumber ->
                create(
                    tournamentId = tournamentId,
                    slotNumber = slotNumber,
                )
            }
    }
}
