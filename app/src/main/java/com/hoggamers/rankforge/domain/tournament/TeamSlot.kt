package com.hoggamers.rankforge.domain.tournament

data class TeamSlot(
    val tournamentId: String,
    val slotNumber: Int,
    val teamName: String = "",
    val group: TournamentGroup? = null,
) {
    init {
        require(tournamentId.isNotBlank()) { "Tournament id is required." }
        require(slotNumber in TOURNAMENT_SLOT_NUMBERS) {
            "Team slot number must be between 1 and 24."
        }
        if (slotNumber > STANDARD_TOURNAMENT_SLOT_CAPACITY) {
            require(group != null) {
                "Group tournament slots above 12 require a group identity."
            }
        }
        group?.let { selectedGroup ->
            require(slotNumber in selectedGroup.slotNumbers) {
                "Team slot number does not belong to its group."
            }
        }
    }

    companion object {
        const val MIN_SLOT_NUMBER = 1
        const val MAX_SLOT_NUMBER = STANDARD_TOURNAMENT_SLOT_CAPACITY
        const val MAX_TOURNAMENT_SLOT_NUMBER = MAX_TOURNAMENT_SLOT_CAPACITY
        val SLOT_NUMBERS: IntRange = MIN_SLOT_NUMBER..MAX_SLOT_NUMBER
        val TOURNAMENT_SLOT_NUMBERS: IntRange = MIN_SLOT_NUMBER..MAX_TOURNAMENT_SLOT_NUMBER

        fun create(
            tournamentId: String,
            slotNumber: Int,
            teamName: String = "",
            group: TournamentGroup? = null,
        ): TeamSlot =
            TeamSlot(
                tournamentId = tournamentId,
                slotNumber = slotNumber,
                teamName = teamName,
                group = group,
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

private val TournamentGroup.slotNumbers: IntRange
    get() = when (this) {
        TournamentGroup.A -> 1..6
        TournamentGroup.B -> 7..12
        TournamentGroup.C -> 13..18
        TournamentGroup.D -> 19..24
    }
