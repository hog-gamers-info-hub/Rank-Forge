package com.hoggamers.rankforge.domain.tournament

data class ClearMatchCorrectionDraftInput(
    val tournamentId: String,
    val matchId: String,
)

class ClearMatchCorrectionDraftUseCase(
    private val repository: TournamentRepository,
) {
    suspend operator fun invoke(input: ClearMatchCorrectionDraftInput) {
        repository.clearMatchCorrectionDraft(input.tournamentId, input.matchId)
    }
}
