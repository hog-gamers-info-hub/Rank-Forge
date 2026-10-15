package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import java.util.UUID

/**
 * Maps the authoritative Room UUID registry and pairing assignments to the
 * inactive V4 identity-write contract. It never creates or derives an identity.
 */
class GroupRotationTeamIdentityCloudV4Mapper(
    private val identityRepository: GroupRotationTeamIdentityLocalRepository,
) {
    suspend fun map(
        request: GroupRotationTeamIdentityV4WriteRequest,
    ): GroupRotationTeamIdentityV4MappingResult = map(
        request = request,
        readResult = identityRepository.read(request.tournament),
    )

    companion object {
        fun map(
            request: GroupRotationTeamIdentityV4WriteRequest,
            readResult: GroupRotationTeamIdentityReadResult,
        ): GroupRotationTeamIdentityV4MappingResult = when (readResult) {
            is GroupRotationTeamIdentityReadResult.Loaded -> mapLoaded(request, readResult.snapshot)
            is GroupRotationTeamIdentityReadResult.Invalid ->
                GroupRotationTeamIdentityV4MappingResult.Invalid(
                    listOf(
                        GroupRotationTeamIdentityV4Issue(
                            GroupRotationTeamIdentityV4IssueCode.LOCAL_IDENTITY_READ_FAILED,
                        ),
                    ),
                )
            GroupRotationTeamIdentityReadResult.Unsupported ->
                GroupRotationTeamIdentityV4MappingResult.Invalid(
                    listOf(
                        GroupRotationTeamIdentityV4Issue(
                            GroupRotationTeamIdentityV4IssueCode.LOCAL_IDENTITY_READ_UNSUPPORTED,
                        ),
                    ),
                )
        }

        private fun mapLoaded(
            request: GroupRotationTeamIdentityV4WriteRequest,
            snapshot: GroupRotationTeamIdentityLocalSnapshot,
        ): GroupRotationTeamIdentityV4MappingResult {
            val issues = validateRequest(request)
            val normalizer = GroupRotationTeamIdentityNormalizer()
            val teamsByUuid = snapshot.teams.associateBy { it.teamUUID }
            val activeAssignedTeamUuids = snapshot.assignments
                .filterNot { it.nameCleared }
                .map { it.teamUUID }
                .toSet()

            snapshot.teams.forEach { team ->
                if (team.tournamentId != request.tournament.id) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                        teamUUID = team.teamUUID,
                    )
                }
                if (!isCanonicalUuid(team.teamUUID)) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.INVALID_TEAM_UUID,
                        teamUUID = team.teamUUID,
                    )
                }
                val cleanedName = normalizer.cleanDisplayName(team.displayName)
                if (cleanedName.isBlank() && team.teamUUID in activeAssignedTeamUuids) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.BLANK_TEAM_NAME,
                        teamUUID = team.teamUUID,
                    )
                }
                if (team.normalizedName != normalizer.normalize(cleanedName)) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.INVALID_NORMALIZED_NAME,
                        teamUUID = team.teamUUID,
                    )
                }
            }
            snapshot.teams.groupBy { it.teamUUID }
                .filterValues { it.size > 1 }
                .keys
                .forEach { teamUUID ->
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.DUPLICATE_TEAM_UUID,
                        teamUUID = teamUUID,
                    )
                }
            snapshot.teams.groupBy { it.normalizedName }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size > 1 }
                .keys
                .forEach { normalizedName ->
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.DUPLICATE_NORMALIZED_NAME,
                    )
                }

            snapshot.assignments.forEach { assignment ->
                val pairingKey = assignment.pairingKey.canonicalKey
                if (assignment.tournamentId != request.tournament.id) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.INVALID_ASSIGNMENT_TOURNAMENT,
                        teamUUID = assignment.teamUUID,
                        pairingKey = pairingKey,
                    )
                }
                if (assignment.pairingKey !in request.tournament.selectedGroupPairings) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.ASSIGNMENT_UNSELECTED_PAIRING,
                        teamUUID = assignment.teamUUID,
                        pairingKey = pairingKey,
                    )
                }
                if (assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.INVALID_LOBBY_SLOT,
                        teamUUID = assignment.teamUUID,
                        pairingKey = pairingKey,
                        lobbySlotNumber = assignment.lobbySlotNumber,
                    )
                }
                if (teamsByUuid[assignment.teamUUID] == null) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.TEAM_UUID_NOT_FOUND,
                        teamUUID = assignment.teamUUID,
                        pairingKey = pairingKey,
                    )
                }
            }
            snapshot.assignments.groupBy { it.pairingKey to it.lobbySlotNumber }
                .filterValues { it.size > 1 }
                .keys
                .forEach { (pairing, lobbySlot) ->
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.DUPLICATE_LOBBY_SLOT,
                        pairingKey = pairing.canonicalKey,
                        lobbySlotNumber = lobbySlot,
                    )
                }
            snapshot.assignments.groupBy { it.pairingKey to it.teamUUID }
                .filterValues { it.size > 1 }
                .keys
                .forEach { (pairing, teamUUID) ->
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.DUPLICATE_TEAM_IN_PAIRING,
                        pairingKey = pairing.canonicalKey,
                        teamUUID = teamUUID,
                    )
                }

            request.pairingsToReplace.groupBy { it.canonicalKey }
                .filterValues { it.size > 1 }
                .keys
                .forEach { pairingKey ->
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.DUPLICATE_PAIRING_SCOPE,
                        pairingKey = pairingKey,
                    )
                }
            request.pairingsToReplace.forEach { pairing ->
                if (pairing !in request.tournament.selectedGroupPairings) {
                    issues += GroupRotationTeamIdentityV4Issue(
                        code = GroupRotationTeamIdentityV4IssueCode.UNSELECTED_PAIRING,
                        pairingKey = pairing.canonicalKey,
                    )
                }
            }

            if (issues.isNotEmpty()) {
                return GroupRotationTeamIdentityV4MappingResult.Invalid(issues.distinct())
            }

            val pairingAssignmentsByKey = snapshot.assignments.groupBy { it.pairingKey }
            return GroupRotationTeamIdentityV4MappingResult.Success(
                GroupRotationTeamIdentityV4WriteParameters(
                    tournamentId = request.tournament.id,
                    teamIdentities = snapshot.teams
                        .sortedBy { it.teamUUID }
                        .map { team ->
                            GroupRotationTeamIdentityV4Payload(
                                teamUUID = team.teamUUID,
                                displayName = team.displayName,
                                normalizedName = team.normalizedName,
                            )
                        },
                    pairingAssignments = request.pairingsToReplace
                        .distinctBy { it.canonicalKey }
                        .sortedBy { it.canonicalKey }
                        .map { pairing ->
                            GroupRotationPairingAssignmentsV4Payload(
                                pairingKey = pairing.canonicalKey,
                                assignments = pairingAssignmentsByKey[pairing]
                                    .orEmpty()
                                    .sortedBy { it.lobbySlotNumber }
                                    .map { assignment ->
                                        GroupRotationPairingTeamAssignmentV4Payload(
                                            lobbySlotNumber = assignment.lobbySlotNumber,
                                            teamUUID = assignment.teamUUID,
                                            nameCleared = assignment.nameCleared,
                                        )
                                    },
                            )
                        },
                    expectedRevision = request.expectedRevision,
                    requestId = request.requestId,
                ),
            )
        }

        private fun validateRequest(
            request: GroupRotationTeamIdentityV4WriteRequest,
        ): MutableList<GroupRotationTeamIdentityV4Issue> = buildList {
            if (request.tournament.format != TournamentFormat.GROUP_ROTATION) {
                add(GroupRotationTeamIdentityV4Issue(GroupRotationTeamIdentityV4IssueCode.NOT_GROUP_ROTATION))
            }
            if (!isCanonicalUuid(request.tournament.id)) {
                add(GroupRotationTeamIdentityV4Issue(GroupRotationTeamIdentityV4IssueCode.INVALID_TOURNAMENT_ID))
            }
            if (!isCanonicalUuid(request.requestId)) {
                add(GroupRotationTeamIdentityV4Issue(GroupRotationTeamIdentityV4IssueCode.INVALID_REQUEST_ID))
            }
            if (request.expectedRevision <= 0) {
                add(
                    GroupRotationTeamIdentityV4Issue(
                        GroupRotationTeamIdentityV4IssueCode.INVALID_EXPECTED_REVISION,
                    ),
                )
            }
        }.toMutableList()

        private fun isCanonicalUuid(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }
}
