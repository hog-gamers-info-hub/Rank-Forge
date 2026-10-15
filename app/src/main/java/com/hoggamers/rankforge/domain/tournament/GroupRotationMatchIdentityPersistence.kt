package com.hoggamers.rankforge.domain.tournament

/** A UUID-backed participant result for a Group Rotation match. */
data class GroupRotationMatchParticipantResult(
    val teamUUID: String,
    val participationStatus: MatchParticipationStatus,
    val placement: Int?,
    val kills: Int,
    val pointAdjustment: Int = 0,
)

data class GroupRotationMatchPersistenceSnapshot(
    val identity: GroupRotationMatchIdentitySnapshot,
    val participantResults: List<GroupRotationMatchParticipantResult>,
)

enum class GroupRotationMatchPersistenceIssueCode {
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    MATCH_NOT_FOUND,
    MATCH_TOURNAMENT_MISMATCH,
    MATCH_PAIRING_MISMATCH,
    UNSELECTED_PAIRING,
    INVALID_PAIRING_KEY,
    INVALID_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    DUPLICATE_TEAM_UUID,
    TEAM_UUID_NOT_FOUND,
    TEAM_UUID_TOURNAMENT_MISMATCH,
    INVALID_TEAM_IDENTITY,
    IMMUTABLE_ASSIGNMENT_CHANGE,
    MATCH_IDENTITY_NOT_SAVED,
    INVALID_PARTICIPATION_STATUS,
    PARTICIPANT_TEAM_NOT_IN_MATCH,
    DUPLICATE_PARTICIPANT_RESULT,
    INVALID_PLACEMENT,
    INVALID_KILLS,
    INVALID_NO_SHOW_RESULT,
    DUPLICATE_PLACEMENT,
    INVALID_STORED_DATA,
    FINALIZED_RESULT_PROTECTED,
}

data class GroupRotationMatchPersistenceIssue(
    val code: GroupRotationMatchPersistenceIssueCode,
    val matchId: String? = null,
    val lobbySlotNumber: Int? = null,
    val teamUUID: String? = null,
)

sealed interface GroupRotationMatchPersistenceResult {
    data class Saved(
        val snapshot: GroupRotationMatchPersistenceSnapshot,
    ) : GroupRotationMatchPersistenceResult

    data class Loaded(
        val snapshot: GroupRotationMatchPersistenceSnapshot,
    ) : GroupRotationMatchPersistenceResult

    data class Invalid(
        val issues: List<GroupRotationMatchPersistenceIssue>,
    ) : GroupRotationMatchPersistenceResult
}

interface GroupRotationMatchIdentityPersistenceRepository {
    suspend fun save(
        tournament: Tournament,
        matchId: String,
        snapshot: GroupRotationMatchIdentitySnapshot,
        participantResults: List<GroupRotationMatchParticipantResult> = emptyList(),
    ): GroupRotationMatchPersistenceResult

    suspend fun read(
        tournament: Tournament,
        matchId: String,
    ): GroupRotationMatchPersistenceResult
}

sealed interface GroupRotationMatchIdentityMode {
    data object Legacy : GroupRotationMatchIdentityMode
    data object UuidBacked : GroupRotationMatchIdentityMode
    data object Invalid : GroupRotationMatchIdentityMode
}

suspend fun GroupRotationMatchIdentityPersistenceRepository.readIdentityMode(
    tournament: Tournament,
    match: Match,
): GroupRotationMatchIdentityMode {
    if (tournament.format != TournamentFormat.GROUP_ROTATION) {
        return GroupRotationMatchIdentityMode.Legacy
    }
    return when (val result = read(tournament, match.id)) {
        is GroupRotationMatchPersistenceResult.Loaded -> GroupRotationMatchIdentityMode.UuidBacked
        is GroupRotationMatchPersistenceResult.Invalid -> {
            if (result.issues.isNotEmpty() && result.issues.all {
                    it.code == GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED
                }
            ) {
                GroupRotationMatchIdentityMode.Legacy
            } else {
                GroupRotationMatchIdentityMode.Invalid
            }
        }
        is GroupRotationMatchPersistenceResult.Saved -> GroupRotationMatchIdentityMode.Invalid
    }
}

object NoOpGroupRotationMatchIdentityPersistenceRepository : GroupRotationMatchIdentityPersistenceRepository {
    override suspend fun save(
        tournament: Tournament,
        matchId: String,
        snapshot: GroupRotationMatchIdentitySnapshot,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): GroupRotationMatchPersistenceResult = GroupRotationMatchPersistenceResult.Invalid(
        listOf(
            GroupRotationMatchPersistenceIssue(
                code = GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED,
                matchId = matchId,
            ),
        ),
    )

    override suspend fun read(
        tournament: Tournament,
        matchId: String,
    ): GroupRotationMatchPersistenceResult = GroupRotationMatchPersistenceResult.Invalid(
        listOf(
            GroupRotationMatchPersistenceIssue(
                code = GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED,
                matchId = matchId,
            ),
        ),
    )
}
