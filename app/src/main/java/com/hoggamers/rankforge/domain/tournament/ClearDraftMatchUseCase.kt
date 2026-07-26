package com.hoggamers.rankforge.domain.tournament

data class ClearDraftMatchInput(
    val tournamentId: String,
    val matchId: String,
)

class ClearDraftMatchUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(input: ClearDraftMatchInput) {
        repository.clearDraftMatch(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
        )
    }
}
