package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult

data class TournamentCloudUploadSnapshot(
    val tournament: Tournament,
    val slots: List<TeamSlot>,
    val rosters: Map<Int, List<RosterPlayer>>,
)

enum class TournamentCloudUploadStage {
    TOURNAMENT,
    TEAM_SLOTS,
}

sealed interface TournamentCloudUploadResult {
    data object Success : TournamentCloudUploadResult
    data object AuthenticationRequired : TournamentCloudUploadResult
    data object ValidationFailure : TournamentCloudUploadResult
    data object AuthorizationFailure : TournamentCloudUploadResult
    data object NetworkFailure : TournamentCloudUploadResult
    data class PartialFailure(
        val completedStage: TournamentCloudUploadStage,
    ) : TournamentCloudUploadResult
}

interface TournamentCloudUploadRepository {
    suspend fun upload(
        snapshot: TournamentCloudUploadSnapshot,
        ownerId: String,
    ): TournamentCloudUploadResult
}

fun interface TournamentCloudUploadAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult>
}
