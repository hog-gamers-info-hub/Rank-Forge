package com.hoggamers.rankforge.domain.tournament

data class GroupRotationTeamIdentityLocalSnapshot(
    val teams: List<GroupRotationTeamIdentity>,
    val assignments: List<GroupRotationPairingAssignment>,
)

sealed interface GroupRotationTeamIdentityReadResult {
    data class Loaded(
        val snapshot: GroupRotationTeamIdentityLocalSnapshot,
    ) : GroupRotationTeamIdentityReadResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityIssue>,
    ) : GroupRotationTeamIdentityReadResult

    /** Used only by compatibility test doubles that do not provide UUID persistence. */
    data object Unsupported : GroupRotationTeamIdentityReadResult
}

sealed interface GroupRotationTeamIdentityWriteResult {
    data class Saved(
        val snapshot: GroupRotationTeamIdentityLocalSnapshot,
    ) : GroupRotationTeamIdentityWriteResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityIssue>,
    ) : GroupRotationTeamIdentityWriteResult

    data object ProtectedHistory : GroupRotationTeamIdentityWriteResult

    /** Used only by compatibility test doubles that do not provide UUID persistence. */
    data object Unsupported : GroupRotationTeamIdentityWriteResult
}

interface GroupRotationTeamIdentityLocalRepository {
    suspend fun read(
        tournament: Tournament,
    ): GroupRotationTeamIdentityReadResult

    suspend fun savePairing(
        tournament: Tournament,
        pairingKey: GroupPairing,
        entries: List<GroupRotationPairingTeamIdentityEntry>,
    ): GroupRotationTeamIdentityWriteResult

    suspend fun renameTeam(
        tournament: Tournament,
        teamUUID: String,
        displayName: String,
    ): GroupRotationTeamIdentityWriteResult
}

fun GroupRotationTeamIdentityLocalSnapshot.toSetupCandidate(
    tournament: Tournament,
): GroupRotationTeamSetupCandidate = GroupRotationTeamSetupCandidate(
    tournamentId = tournament.id,
    entries = assignments.mapNotNull { assignment ->
        teams.firstOrNull { it.teamUUID == assignment.teamUUID }?.let { team ->
            GroupRotationPairingTeamEntry(
                pairing = assignment.pairingKey,
                lobbySlotNumber = assignment.lobbySlotNumber,
                teamName = team.displayName,
            )
        }
    },
)
