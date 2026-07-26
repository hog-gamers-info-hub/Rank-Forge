package com.hoggamers.rankforge.domain.tournament

data class SaveMatchDraftValueInput(
    val tournamentId: String,
    val matchId: String,
    val teamSlotNumber: Int,
    val placementInput: String? = null,
    val killsInput: String? = null,
)

class SaveMatchDraftValueUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(input: SaveMatchDraftValueInput) {
        repository.saveDraftMatchValue(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
            teamSlotNumber = input.teamSlotNumber,
            placementInput = input.placementInput,
            killsInput = input.killsInput,
        )
    }
}
