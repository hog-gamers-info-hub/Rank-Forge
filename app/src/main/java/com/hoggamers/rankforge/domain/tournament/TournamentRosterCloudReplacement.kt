package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.RevisionConflict

data class TournamentRosterCloudReplacement(
    val tournament: Tournament,
    val slots: List<TeamSlot>,
    val rosters: Map<Int, List<RosterPlayer>>,
    val expectedCloudRevision: Int?,
)

sealed interface TournamentRosterCloudReplacementResult {
    data class Success(val newCloudRevision: Int) : TournamentRosterCloudReplacementResult
    data object AuthenticationRequired : TournamentRosterCloudReplacementResult
    data object ValidationFailure : TournamentRosterCloudReplacementResult
    data object AuthorizationFailure : TournamentRosterCloudReplacementResult
    data object NetworkFailure : TournamentRosterCloudReplacementResult
    data object BlockedByExistingMatches : TournamentRosterCloudReplacementResult
    data class Conflict(val conflict: RevisionConflict) : TournamentRosterCloudReplacementResult
    data object UnknownFailure : TournamentRosterCloudReplacementResult
}

interface TournamentRosterCloudReplacementRepository {
    suspend fun replace(
        snapshot: TournamentRosterCloudReplacement,
        ownerId: String,
    ): TournamentRosterCloudReplacementResult
}

fun interface TournamentRosterCloudReplacementAction {
    suspend operator fun invoke(tournamentId: String): QueueAwareActionResult<TournamentRosterCloudReplacementResult>
}

fun interface TournamentRosterCloudReplacementRetryAction {
    suspend fun executeForRetry(tournamentId: String): TournamentRosterCloudReplacementResult
}
