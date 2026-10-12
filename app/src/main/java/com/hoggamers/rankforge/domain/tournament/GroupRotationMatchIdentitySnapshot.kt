package com.hoggamers.rankforge.domain.tournament

/** A saved UUID-backed identity occupying one local Group Rotation lobby slot. */
data class GroupRotationMatchTeamIdentity(
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val displayName: String,
)

/** Read-only identity projection for one Group Rotation match pairing. */
data class GroupRotationMatchIdentitySnapshot(
    val tournamentId: String,
    val pairing: GroupPairing,
    val teams: List<GroupRotationMatchTeamIdentity>,
)

enum class GroupRotationMatchIdentitySnapshotIssueCode {
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    UNSELECTED_PAIRING,
    INVALID_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    DUPLICATE_TEAM_UUID,
    TEAM_UUID_NOT_FOUND,
    TEAM_UUID_TOURNAMENT_MISMATCH,
    INVALID_TEAM_IDENTITY,
    CONFLICTING_TEAM_IDENTITY,
}

data class GroupRotationMatchIdentitySnapshotIssue(
    val code: GroupRotationMatchIdentitySnapshotIssueCode,
    val lobbySlotNumber: Int? = null,
    val teamUUID: String? = null,
    val pairingKey: String? = null,
)

sealed interface GroupRotationMatchIdentitySnapshotResult {
    data class Loaded(
        val snapshot: GroupRotationMatchIdentitySnapshot,
    ) : GroupRotationMatchIdentitySnapshotResult

    data class Invalid(
        val issues: List<GroupRotationMatchIdentitySnapshotIssue>,
    ) : GroupRotationMatchIdentitySnapshotResult
}

/** Projects only already-saved Group Rotation identities into match-local lobby positions. */
class GroupRotationMatchIdentitySnapshotResolver {
    private val normalizer = GroupRotationTeamIdentityNormalizer()

    fun resolve(
        tournament: Tournament,
        pairing: GroupPairing,
        saved: GroupRotationTeamIdentityLocalSnapshot,
    ): GroupRotationMatchIdentitySnapshotResult {
        val issues = mutableListOf<GroupRotationMatchIdentitySnapshotIssue>()
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += issue(GroupRotationMatchIdentitySnapshotIssueCode.NOT_GROUP_ROTATION)
        }
        if (pairing !in tournament.selectedGroupPairings) {
            issues += issue(
                GroupRotationMatchIdentitySnapshotIssueCode.UNSELECTED_PAIRING,
                pairingKey = pairing.canonicalKey,
            )
        }

        val identitiesByUuid = linkedMapOf<String, GroupRotationTeamIdentity>()
        saved.teams.forEach { identity ->
            if (identity.tournamentId != tournament.id) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                    teamUUID = identity.teamUUID,
                )
            }
            if (
                identity.teamUUID.isBlank() ||
                    identity.displayName.isBlank() ||
                    identity.normalizedName.isBlank() ||
                    identity.normalizedName != normalizer.normalize(identity.displayName)
            ) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.INVALID_TEAM_IDENTITY,
                    teamUUID = identity.teamUUID,
                )
            }
            if (identitiesByUuid.put(identity.teamUUID, identity) != null) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.CONFLICTING_TEAM_IDENTITY,
                    teamUUID = identity.teamUUID,
                )
            }
        }

        saved.assignments.forEach { assignment ->
            if (assignment.tournamentId != tournament.id) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.TOURNAMENT_ID_MISMATCH,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.pairingKey !in tournament.selectedGroupPairings) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.UNSELECTED_PAIRING,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.INVALID_LOBBY_SLOT,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (identitiesByUuid[assignment.teamUUID] == null) {
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.TEAM_UUID_NOT_FOUND,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
        }

        val selectedAssignments = saved.assignments.filter { it.pairingKey == pairing }
        selectedAssignments
            .groupBy { it.lobbySlotNumber }
            .filterValues { it.size > 1 }
            .forEach { (lobbySlotNumber, assignments) ->
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.DUPLICATE_LOBBY_SLOT,
                    lobbySlotNumber = lobbySlotNumber,
                    pairingKey = pairing.canonicalKey,
                    teamUUID = assignments.first().teamUUID,
                )
            }
        selectedAssignments
            .groupBy { it.teamUUID }
            .filterValues { it.size > 1 }
            .forEach { (teamUUID, assignments) ->
                issues += issue(
                    GroupRotationMatchIdentitySnapshotIssueCode.DUPLICATE_TEAM_UUID,
                    lobbySlotNumber = assignments.first().lobbySlotNumber,
                    pairingKey = pairing.canonicalKey,
                    teamUUID = teamUUID,
                )
            }

        if (issues.isNotEmpty()) {
            return GroupRotationMatchIdentitySnapshotResult.Invalid(issues.distinct())
        }

        return GroupRotationMatchIdentitySnapshotResult.Loaded(
            GroupRotationMatchIdentitySnapshot(
                tournamentId = tournament.id,
                pairing = pairing,
                teams = selectedAssignments
                    .sortedBy { it.lobbySlotNumber }
                    .map { assignment ->
                        val identity = identitiesByUuid.getValue(assignment.teamUUID)
                        GroupRotationMatchTeamIdentity(
                            lobbySlotNumber = assignment.lobbySlotNumber,
                            teamUUID = identity.teamUUID,
                            displayName = identity.displayName,
                        )
                    },
            ),
        )
    }

    private fun issue(
        code: GroupRotationMatchIdentitySnapshotIssueCode,
        lobbySlotNumber: Int? = null,
        teamUUID: String? = null,
        pairingKey: String? = null,
    ) = GroupRotationMatchIdentitySnapshotIssue(
        code = code,
        lobbySlotNumber = lobbySlotNumber,
        teamUUID = teamUUID,
        pairingKey = pairingKey,
    )
}
