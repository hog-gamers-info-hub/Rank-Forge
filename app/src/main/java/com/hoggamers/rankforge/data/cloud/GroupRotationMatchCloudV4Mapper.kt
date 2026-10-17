package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import java.util.UUID

/**
 * UUID-only mapper for the inactive V4 Group Rotation contract.
 *
 * The registry and pairing assignments are validation authority. Only the
 * immutable match lobby snapshot and UUID participant rows are sent to the
 * V4 RPCs; no numeric TeamSlot or team-name identity is constructed here.
 */
object GroupRotationMatchCloudV4Mapper {
    fun mapDraft(
        tournament: Tournament,
        request: GroupRotationV4DraftWriteRequest,
    ): GroupRotationV4MappingResult<GroupRotationV4DraftWriteParameters> {
        val issues = validateRequestEnvelope(
            tournament = tournament,
            tournamentId = request.tournamentId,
            matchId = request.matchId,
            requestId = request.requestId,
            expectedRevision = request.expectedRevision,
        )
        val sourceIssues = validateSource(
            tournament = tournament,
            tournamentId = request.tournamentId,
            matchId = request.matchId,
            source = request.source,
            requireNonEmptyIdentity = false,
        )
        val resultIssues = validateResults(
            identitySnapshot = request.source.identitySnapshot,
            results = request.source.identitySnapshot.participantResults,
            requireFinalized = false,
        )
        val allIssues = (issues + sourceIssues + resultIssues).distinct()
        if (allIssues.isNotEmpty()) return GroupRotationV4MappingResult.Invalid(allIssues)

        val identitySnapshot = request.source.identitySnapshot.identity
        return GroupRotationV4MappingResult.Success(
            GroupRotationV4DraftWriteParameters(
                tournamentId = request.tournamentId,
                match = identitySnapshot.toMatchPayload(request.tournamentId, request.matchId),
                matchIdentity = identitySnapshot.teams
                    .sortedBy { it.lobbySlotNumber }
                    .map { team ->
                        GroupRotationUuidMatchLobbySnapshotPayload(
                            lobbySlotNumber = team.lobbySlotNumber,
                            teamUUID = team.teamUUID,
                        )
                    },
                matchResults = request.source.identitySnapshot.participantResults
                    .sortedBy { it.teamUUID }
                    .map { it.toPayload() },
                expectedRevision = request.expectedRevision,
                requestId = request.requestId,
            ),
        )
    }

    fun mapFinalization(
        tournament: Tournament,
        request: GroupRotationV4FinalizationRequest,
    ): GroupRotationV4MappingResult<GroupRotationV4FinalizationParameters> {
        val issues = validateRequestEnvelope(
            tournament = tournament,
            tournamentId = request.tournamentId,
            matchId = request.matchId,
            requestId = request.requestId,
            expectedRevision = request.expectedRevision,
        )
        val sourceIssues = validateSource(
            tournament = tournament,
            tournamentId = request.tournamentId,
            matchId = request.matchId,
            source = request.source,
            requireNonEmptyIdentity = true,
        )
        val resultIssues = validateResults(
            identitySnapshot = request.source.identitySnapshot,
            results = request.source.identitySnapshot.participantResults,
            requireFinalized = true,
        )
        val allIssues = (issues + sourceIssues + resultIssues).distinct()
        if (allIssues.isNotEmpty()) return GroupRotationV4MappingResult.Invalid(allIssues)

        return GroupRotationV4MappingResult.Success(
            GroupRotationV4FinalizationParameters(
                tournamentId = request.tournamentId,
                matchId = request.matchId,
                matchResults = request.source.identitySnapshot.participantResults
                    .sortedBy { it.teamUUID }
                    .map { it.toPayload() },
                expectedRevision = request.expectedRevision,
                requestId = request.requestId,
            ),
        )
    }

    private fun validateRequestEnvelope(
        tournament: Tournament,
        tournamentId: String,
        matchId: String,
        requestId: String,
        expectedRevision: Int,
    ): List<GroupRotationMatchCloudV4Issue> {
        val issues = mutableListOf<GroupRotationMatchCloudV4Issue>()
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += issue(GroupRotationMatchCloudV4IssueCode.NOT_GROUP_ROTATION)
        }
        if (!isCanonicalUuid(tournamentId)) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_TOURNAMENT_ID)
        if (!isCanonicalUuid(matchId)) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_MATCH_ID)
        if (!isCanonicalUuid(requestId)) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_REQUEST_ID)
        if (expectedRevision < 0) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_EXPECTED_REVISION)
        if (tournament.id != tournamentId) {
            issues += issue(GroupRotationMatchCloudV4IssueCode.TOURNAMENT_ID_MISMATCH)
        }
        return issues
    }

    private fun validateSource(
        tournament: Tournament,
        tournamentId: String,
        matchId: String,
        source: GroupRotationMatchCloudV4RequestSource,
        requireNonEmptyIdentity: Boolean,
    ): List<GroupRotationMatchCloudV4Issue> {
        val issues = mutableListOf<GroupRotationMatchCloudV4Issue>()
        val persistence = source.identitySnapshot
        val identity = persistence.identity
        val saved = source.savedIdentitySnapshot
        val normalizer = GroupRotationTeamIdentityNormalizer()

        if (requireNonEmptyIdentity && persistence.identity.teams.isEmpty()) {
            issues += issue(GroupRotationMatchCloudV4IssueCode.EMPTY_MATCH_IDENTITY)
        }

        if (identity.tournamentId != tournamentId) {
            issues += issue(GroupRotationMatchCloudV4IssueCode.MATCH_IDENTITY_TOURNAMENT_MISMATCH)
        }
        if (identity.pairing !in tournament.selectedGroupPairings) {
            issues += issue(GroupRotationMatchCloudV4IssueCode.UNSELECTED_PAIRING)
        }

        val activeAssignedTeamUUIDs = saved.assignments
            .filterNot { it.nameCleared }
            .map { it.teamUUID }
            .toSet()
        val validTeams = saved.teams.map { team ->
            if (team.tournamentId != tournamentId) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.TEAM_UUID_TOURNAMENT_MISMATCH, teamUUID = team.teamUUID)
            }
            val normalizedName = normalizer.normalize(team.displayName)
            if (
                !isCanonicalUuid(team.teamUUID) ||
                    (team.displayName.isBlank() &&
                        (team.normalizedName.isNotBlank() || team.teamUUID in activeAssignedTeamUUIDs)) ||
                    (team.displayName.isNotBlank() && team.normalizedName != normalizedName)
            ) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_UUID_IDENTITY, teamUUID = team.teamUUID)
            }
            team
        }
        validTeams.groupBy { it.teamUUID }.filterValues { it.size > 1 }.keys.forEach { teamUUID ->
            issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_TEAM_UUID, teamUUID = teamUUID)
        }
        validTeams
            .filter { it.normalizedName.isNotBlank() }
            .groupBy { it.normalizedName }
            .filterValues { it.size > 1 }
            .keys
            .forEach {
                issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_NORMALIZED_NAME)
            }
        val teamsByUuid = validTeams.associateBy { it.teamUUID }

        validateAssignments(
            tournament = tournament,
            tournamentId = tournamentId,
            assignments = saved.assignments,
            teamsByUuid = teamsByUuid,
            issues = issues,
        )

        val snapshotTeams = identity.teams
        snapshotTeams.groupBy { it.lobbySlotNumber }.filterValues { it.size > 1 }.keys.forEach { lobby ->
            issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_LOBBY_SLOT, lobbySlotNumber = lobby)
        }
        snapshotTeams.groupBy { it.teamUUID }.filterValues { it.size > 1 }.keys.forEach { teamUUID ->
            issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_TEAM_UUID, teamUUID = teamUUID)
        }
        snapshotTeams.forEach { team ->
            if (team.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchCloudV4IssueCode.INVALID_PAIRING_ASSIGNMENT,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            }
            if (!isCanonicalUuid(team.teamUUID)) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_UUID_IDENTITY, teamUUID = team.teamUUID)
            }
            if (teamsByUuid[team.teamUUID] == null) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.TEAM_UUID_NOT_FOUND, teamUUID = team.teamUUID)
            }
        }

        if (matchId.isBlank()) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_MATCH_ID)
        return issues
    }

    private fun validateAssignments(
        tournament: Tournament,
        tournamentId: String,
        assignments: List<GroupRotationPairingAssignment>,
        teamsByUuid: Map<String, GroupRotationTeamIdentity>,
        issues: MutableList<GroupRotationMatchCloudV4Issue>,
    ) {
        assignments.forEach { assignment ->
            if (assignment.tournamentId != tournamentId) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.TEAM_UUID_TOURNAMENT_MISMATCH, teamUUID = assignment.teamUUID)
            }
            if (assignment.pairingKey !in tournament.selectedGroupPairings) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.UNSELECTED_PAIRING)
            }
            if (assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                !isCanonicalUuid(assignment.teamUUID)
            ) {
                issues += issue(
                    GroupRotationMatchCloudV4IssueCode.INVALID_PAIRING_ASSIGNMENT,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    teamUUID = assignment.teamUUID,
                )
            }
            if (teamsByUuid[assignment.teamUUID] == null) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.TEAM_UUID_NOT_FOUND, teamUUID = assignment.teamUUID)
            }
        }
        assignments.groupBy { it.pairingKey to it.lobbySlotNumber }
            .filterValues { it.size > 1 }
            .forEach { (key, _) ->
                issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_LOBBY_SLOT, lobbySlotNumber = key.second)
            }
        assignments.groupBy { it.pairingKey to it.teamUUID }
            .filterValues { it.size > 1 }
            .forEach { (key, _) ->
                issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_ASSIGNED_TEAM_UUID, teamUUID = key.second)
            }
    }

    private fun validateResults(
        identitySnapshot: GroupRotationMatchPersistenceSnapshot,
        results: List<GroupRotationMatchParticipantResult>,
        requireFinalized: Boolean,
    ): List<GroupRotationMatchCloudV4Issue> {
        val issues = mutableListOf<GroupRotationMatchCloudV4Issue>()
        val assigned = identitySnapshot.identity.teams.map { it.teamUUID }.toSet()
        results.groupBy { it.teamUUID }.filterValues { it.size > 1 }.keys.forEach { teamUUID ->
            issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_PARTICIPANT_RESULT, teamUUID = teamUUID)
        }
        results.groupBy { it.placement }.filterKeys { it != null }.filterValues { it.size > 1 }
            .keys.forEach { placement ->
                issues += issue(GroupRotationMatchCloudV4IssueCode.DUPLICATE_PLACEMENT)
            }
        results.forEach { result ->
            if (!isCanonicalUuid(result.teamUUID) || result.teamUUID !in assigned) {
                issues += issue(
                    GroupRotationMatchCloudV4IssueCode.PARTICIPANT_TEAM_NOT_IN_MATCH,
                    teamUUID = result.teamUUID,
                )
            }
            if (result.kills < 0) issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_KILLS, teamUUID = result.teamUUID)
            when (result.participationStatus) {
                MatchParticipationStatus.PARTICIPATED -> {
                    if (result.placement != null && result.placement !in 1..12) {
                        issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_PLACEMENT, teamUUID = result.teamUUID)
                    }
                    if (requireFinalized && result.placement == null) {
                        issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_PLACEMENT, teamUUID = result.teamUUID)
                    }
                }
                MatchParticipationStatus.NO_SHOW -> {
                    if (result.placement != null || result.kills != 0) {
                        issues += issue(GroupRotationMatchCloudV4IssueCode.INVALID_NO_SHOW_RESULT, teamUUID = result.teamUUID)
                    }
                }
            }
        }

        if (requireFinalized) {
            val participated = results.filter {
                it.teamUUID in assigned && it.participationStatus == MatchParticipationStatus.PARTICIPATED
            }
            assigned.filterNot { teamUUID -> results.any { it.teamUUID == teamUUID } }
                .forEach { teamUUID ->
                    issues += issue(GroupRotationMatchCloudV4IssueCode.MISSING_PARTICIPANT_RESULT, teamUUID = teamUUID)
                }
            if (participated.isEmpty()) issues += issue(GroupRotationMatchCloudV4IssueCode.NO_PARTICIPATED_TEAM)
            val expected = (1..participated.size).toSet()
            if (participated.mapNotNull { it.placement }.toSet() != expected) {
                issues += issue(GroupRotationMatchCloudV4IssueCode.PLACEMENTS_NOT_GAP_FREE)
            }
        }
        return issues
    }

    private fun com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot.toMatchPayload(
        tournamentId: String,
        matchId: String,
    ) = GroupRotationV4MatchPayload(
        id = matchId,
        tournamentId = tournamentId,
        pairingKey = pairing.canonicalKey,
    )

    private fun GroupRotationMatchParticipantResult.toPayload() = GroupRotationUuidParticipantResultPayload(
        teamUUID = teamUUID,
        participationStatus = participationStatus.name,
        placement = placement,
        kills = kills,
        pointAdjustment = pointAdjustment,
    )

    private fun issue(
        code: GroupRotationMatchCloudV4IssueCode,
        teamUUID: String? = null,
        lobbySlotNumber: Int? = null,
    ) = GroupRotationMatchCloudV4Issue(code, teamUUID, lobbySlotNumber)

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}
