package com.hoggamers.rankforge.domain.tournament

class SaveTeamSlotNamesUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(
        tournamentId: String,
        teamNamesBySlotNumber: Map<Int, String>,
    ) {
        repository.saveTeamNames(
            tournamentId = tournamentId,
            teamNamesBySlotNumber = teamNamesBySlotNumber.mapValues { (_, teamName) -> teamName.trim() },
        )
    }
}
