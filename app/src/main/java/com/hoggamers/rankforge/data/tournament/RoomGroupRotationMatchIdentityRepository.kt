package com.hoggamers.rankforge.data.tournament

import androidx.room.withTransaction
import com.hoggamers.rankforge.data.local.GroupRotationMatchIdentityHeaderEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchParticipantResultEntity
import com.hoggamers.rankforge.data.local.GroupRotationMatchTeamAssignmentEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshotResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RoomGroupRotationMatchIdentityRepository @Inject constructor(
    private val database: RankForgeDatabase,
) : GroupRotationMatchIdentityPersistenceRepository {
    private val normalizer = GroupRotationTeamIdentityNormalizer()
    private val snapshotResolver = com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshotResolver()

    /** Must be called from the caller's transaction after the snapshot was validated. */
    internal suspend fun clearParticipantResultsWithinTransaction(matchId: String) {
        database.groupRotationMatchParticipantResultDao().deleteByMatchId(matchId)
    }

    /** Validates a new match identity before its parent Match row is inserted. */
    internal suspend fun validateForCreation(
        tournament: Tournament,
        match: com.hoggamers.rankforge.domain.tournament.Match,
        snapshot: GroupRotationMatchIdentitySnapshot,
    ): List<GroupRotationMatchPersistenceIssue> {
        val identities = database.groupRotationTeamIdentityDao()
            .readByTournamentId(tournament.id)
        return (
            validateMatchAndSnapshot(
                tournament = tournament,
                matchId = match.id,
                matchTournamentId = match.tournamentId,
                matchPairingKey = match.groupPairing?.canonicalKey,
                snapshot = snapshot,
                identities = identities.map { it.toDomain() },
            ) + validateParticipantResults(
                matchId = match.id,
                results = emptyList(),
                assignedTeamUuids = snapshot.teams.map { it.teamUUID }.toSet(),
                identitiesByUuid = identities.associateBy { it.teamUUID },
            )
        ).distinct()
    }

    /** Persists the UUID identity rows after the parent Match row is in the same transaction. */
    internal suspend fun persistForCreation(
        tournament: Tournament,
        match: com.hoggamers.rankforge.domain.tournament.Match,
        snapshot: GroupRotationMatchIdentitySnapshot,
    ): GroupRotationMatchIdentitySnapshot {
        val identities = database.groupRotationTeamIdentityDao()
            .readByTournamentId(tournament.id)
        database.groupRotationMatchIdentityHeaderDao().insert(
            GroupRotationMatchIdentityHeaderEntity(
                matchId = match.id,
                tournamentId = tournament.id,
                pairingKey = snapshot.pairing.canonicalKey,
            ),
        )
        database.groupRotationMatchTeamAssignmentDao().insertAll(
            snapshot.teams.sortedBy { it.lobbySlotNumber }.map { team ->
                GroupRotationMatchTeamAssignmentEntity(
                    matchId = match.id,
                    tournamentId = tournament.id,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            },
        )
        return currentIdentitySnapshot(snapshot, identities.associateBy { it.teamUUID })
    }

    override suspend fun save(
        tournament: Tournament,
        matchId: String,
        snapshot: GroupRotationMatchIdentitySnapshot,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): GroupRotationMatchPersistenceResult = database.withTransaction {
        val match = database.matchDao().readById(matchId)
            ?: return@withTransaction invalid(
                GroupRotationMatchPersistenceIssueCode.MATCH_NOT_FOUND,
                matchId = matchId,
            )
        if (match.status != com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT.name) {
            return@withTransaction invalid(
                GroupRotationMatchPersistenceIssueCode.FINALIZED_RESULT_PROTECTED,
                matchId = matchId,
            )
        }
        val identities = database.groupRotationTeamIdentityDao()
            .readByTournamentId(tournament.id)
        val commonIssues = validateMatchAndSnapshot(
            tournament = tournament,
            matchId = matchId,
            matchTournamentId = match.tournamentId,
            matchPairingKey = match.groupPairingKey,
            snapshot = snapshot,
            identities = identities.map { it.toDomain() },
        )
        if (commonIssues.isNotEmpty()) {
            return@withTransaction GroupRotationMatchPersistenceResult.Invalid(commonIssues)
        }

        val identitiesByUuid = identities.associateBy { it.teamUUID }
        val participantIssues = validateParticipantResults(
            matchId = matchId,
            results = participantResults,
            assignedTeamUuids = snapshot.teams.map { it.teamUUID }.toSet(),
            identitiesByUuid = identitiesByUuid,
        )
        if (participantIssues.isNotEmpty()) {
            return@withTransaction GroupRotationMatchPersistenceResult.Invalid(participantIssues)
        }

        val headerDao = database.groupRotationMatchIdentityHeaderDao()
        val assignmentDao = database.groupRotationMatchTeamAssignmentDao()
        val resultDao = database.groupRotationMatchParticipantResultDao()
        val existingHeader = headerDao.readByMatchId(matchId)
        val incomingAssignments = snapshot.teams
            .sortedBy { it.lobbySlotNumber }
            .map { team ->
                GroupRotationMatchTeamAssignmentEntity(
                    matchId = matchId,
                    tournamentId = tournament.id,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            }

        if (existingHeader == null) {
            headerDao.insert(
                GroupRotationMatchIdentityHeaderEntity(
                    matchId = matchId,
                    tournamentId = tournament.id,
                    pairingKey = snapshot.pairing.canonicalKey,
                ),
            )
            assignmentDao.insertAll(incomingAssignments)
        } else {
            val existingAssignments = assignmentDao.readByMatchId(matchId)
            if (
                existingHeader.tournamentId != tournament.id ||
                    existingHeader.pairingKey != snapshot.pairing.canonicalKey ||
                    existingAssignments != incomingAssignments
            ) {
                return@withTransaction invalid(
                    GroupRotationMatchPersistenceIssueCode.IMMUTABLE_ASSIGNMENT_CHANGE,
                    matchId = matchId,
                )
            }
        }

        resultDao.deleteByMatchId(matchId)
        resultDao.insertAll(
            participantResults.map { result ->
                GroupRotationMatchParticipantResultEntity(
                    matchId = matchId,
                    tournamentId = tournament.id,
                    teamUUID = result.teamUUID,
                    participationStatus = result.participationStatus.name,
                    placement = result.placement,
                    kills = result.kills,
                    pointAdjustment = result.pointAdjustment,
                )
            },
        )

        GroupRotationMatchPersistenceResult.Saved(
            snapshot = GroupRotationMatchPersistenceSnapshot(
                identity = currentIdentitySnapshot(snapshot, identitiesByUuid),
                participantResults = participantResults,
            ),
        )
    }

    override suspend fun read(
        tournament: Tournament,
        matchId: String,
    ): GroupRotationMatchPersistenceResult = database.withTransaction {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return@withTransaction invalid(GroupRotationMatchPersistenceIssueCode.NOT_GROUP_ROTATION, matchId)
        }
        val match = database.matchDao().readById(matchId)
            ?: return@withTransaction invalid(GroupRotationMatchPersistenceIssueCode.MATCH_NOT_FOUND, matchId)
        val header = database.groupRotationMatchIdentityHeaderDao().readByMatchId(matchId)
            ?: return@withTransaction invalid(
                GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED,
                matchId,
            )
        val identities = database.groupRotationTeamIdentityDao()
            .readByTournamentId(tournament.id)
        val pairing = runCatching { GroupPairing.fromCanonicalKey(header.pairingKey) }.getOrNull()
            ?: return@withTransaction invalid(
                GroupRotationMatchPersistenceIssueCode.INVALID_PAIRING_KEY,
                matchId,
            )
        val assignmentDao = database.groupRotationMatchTeamAssignmentDao()
        val storedAssignments = assignmentDao.readByMatchId(matchId)
        val storedAssignmentIssues = validateStoredHeaderAndAssignments(
            tournament = tournament,
            matchId = matchId,
            header = header,
            assignments = storedAssignments,
        )
        if (storedAssignmentIssues.isNotEmpty()) {
            return@withTransaction GroupRotationMatchPersistenceResult.Invalid(storedAssignmentIssues)
        }
        val identitySnapshot = GroupRotationMatchIdentitySnapshot(
            tournamentId = tournament.id,
            pairing = pairing,
            teams = storedAssignments
                .sortedBy { it.lobbySlotNumber }
                .map { assignment ->
                    identities.firstOrNull { it.teamUUID == assignment.teamUUID }?.let { identity ->
                        GroupRotationMatchTeamIdentity(
                            lobbySlotNumber = assignment.lobbySlotNumber,
                            teamUUID = identity.teamUUID,
                            displayName = identity.displayName,
                        )
                    } ?: return@withTransaction invalid(
                        GroupRotationMatchPersistenceIssueCode.TEAM_UUID_NOT_FOUND,
                        matchId,
                        teamUUID = assignment.teamUUID,
                    )
                },
        )
        val effectiveIdentitySnapshot = if (match.status == MatchStatus.DRAFT.name) {
            when (val current = resolveCurrentDraftSnapshot(tournament, pairing, identities)) {
                is GroupRotationMatchIdentitySnapshotResult.Loaded -> current.snapshot
                is GroupRotationMatchIdentitySnapshotResult.Invalid ->
                    return@withTransaction invalidCurrentDraftSnapshot(matchId, current.issues)
            }
        } else {
            identitySnapshot
        }
        val effectiveAssignments = effectiveIdentitySnapshot.teams
            .sortedBy { it.lobbySlotNumber }
            .map { team ->
                GroupRotationMatchTeamAssignmentEntity(
                    matchId = matchId,
                    tournamentId = tournament.id,
                    lobbySlotNumber = team.lobbySlotNumber,
                    teamUUID = team.teamUUID,
                )
            }
        val issues = validateMatchAndSnapshot(
            tournament = tournament,
            matchId = matchId,
            matchTournamentId = match.tournamentId,
            matchPairingKey = match.groupPairingKey,
            snapshot = effectiveIdentitySnapshot,
            identities = identities.map { it.toDomain() },
        ) + validateStoredHeaderAndAssignments(
            tournament = tournament,
            matchId = matchId,
            header = header,
            assignments = effectiveAssignments,
        )
        if (issues.isNotEmpty()) {
            return@withTransaction GroupRotationMatchPersistenceResult.Invalid(issues.distinct())
        }
        val identitiesByUuid = identities.associateBy { it.teamUUID }
        val participantEntities = database.groupRotationMatchParticipantResultDao().readByMatchId(matchId)
        val assignedTeamUuids = effectiveIdentitySnapshot.teams.map { it.teamUUID }.toSet()
        val effectiveParticipantEntities = if (match.status == MatchStatus.DRAFT.name) {
            participantEntities.filter { it.teamUUID in assignedTeamUuids }
        } else {
            participantEntities
        }
        val participantIssues = validateParticipantEntities(
            matchId = matchId,
            results = effectiveParticipantEntities,
            assignedTeamUuids = assignedTeamUuids,
            identitiesByUuid = identitiesByUuid,
        )
        if (participantIssues.isNotEmpty()) {
            return@withTransaction GroupRotationMatchPersistenceResult.Invalid(participantIssues)
        }
        if (match.status == MatchStatus.DRAFT.name && storedAssignments != effectiveAssignments) {
            val oldUuidByLobby = storedAssignments.associate { it.lobbySlotNumber to it.teamUUID }
            val newLobbyByUuid = effectiveAssignments.associate { it.teamUUID to it.lobbySlotNumber }
            val currentDraftValues = database.matchDraftValueDao().observeByMatchId(matchId).first()
            val remappedDraftValues = currentDraftValues
                .mapNotNull { value ->
                    val teamUUID = oldUuidByLobby[value.teamSlotNumber]
                    val newLobbySlot = teamUUID?.let(newLobbyByUuid::get)
                    newLobbySlot?.let { value.copy(teamSlotNumber = it) }
                }
                .sortedBy { it.teamSlotNumber }
            if (remappedDraftValues != currentDraftValues) {
                database.matchDraftValueDao().deleteByMatchId(matchId)
                database.matchDraftValueDao().upsertAll(remappedDraftValues)
            }
            assignmentDao.deleteByMatchId(matchId)
            if (effectiveAssignments.isNotEmpty()) {
                assignmentDao.insertAll(effectiveAssignments)
            }
            if (effectiveParticipantEntities.size != participantEntities.size) {
                database.groupRotationMatchParticipantResultDao().deleteByMatchId(matchId)
                if (effectiveParticipantEntities.isNotEmpty()) {
                    database.groupRotationMatchParticipantResultDao().insertAll(effectiveParticipantEntities)
                }
            }
        }
        GroupRotationMatchPersistenceResult.Loaded(
            GroupRotationMatchPersistenceSnapshot(
                identity = effectiveIdentitySnapshot,
                participantResults = effectiveParticipantEntities.map { entity ->
                    GroupRotationMatchParticipantResult(
                        teamUUID = entity.teamUUID,
                        participationStatus = MatchParticipationStatus.valueOf(entity.participationStatus),
                        placement = entity.placement,
                        kills = entity.kills,
                        pointAdjustment = entity.pointAdjustment,
                    )
                },
            ),
        )
    }

    private suspend fun resolveCurrentDraftSnapshot(
        tournament: Tournament,
        pairing: GroupPairing,
        identities: List<com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity>,
    ): GroupRotationMatchIdentitySnapshotResult {
        val assignmentEntities = database.groupRotationPairingTeamIdentityAssignmentDao()
            .readByTournamentId(tournament.id)
        val assignments = mutableListOf<GroupRotationPairingAssignment>()
        assignmentEntities.forEach { entity ->
            val assignmentPairing = runCatching { GroupPairing.fromCanonicalKey(entity.pairingKey) }
                .getOrNull()
                ?: return GroupRotationMatchIdentitySnapshotResult.Invalid(
                    listOf(
                        com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshotIssue(
                            code = com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshotIssueCode.UNSELECTED_PAIRING,
                            lobbySlotNumber = entity.lobbySlotNumber,
                            teamUUID = entity.teamUUID,
                            pairingKey = entity.pairingKey,
                        ),
                    ),
                )
            assignments += GroupRotationPairingAssignment(
                tournamentId = entity.tournamentId,
                pairingKey = assignmentPairing,
                lobbySlotNumber = entity.lobbySlotNumber,
                teamUUID = entity.teamUUID,
                nameCleared = entity.nameCleared,
            )
        }
        return snapshotResolver.resolve(
            tournament = tournament,
            pairing = pairing,
            saved = GroupRotationTeamIdentityLocalSnapshot(
                teams = identities.map { identity ->
                    GroupRotationTeamIdentity(
                        tournamentId = identity.tournamentId,
                        teamUUID = identity.teamUUID,
                        displayName = identity.displayName,
                        normalizedName = identity.normalizedName,
                    )
                },
                assignments = assignments,
            ),
        )
    }

    private fun invalidCurrentDraftSnapshot(
        matchId: String,
        issues: List<com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshotIssue>,
    ): GroupRotationMatchPersistenceResult.Invalid = GroupRotationMatchPersistenceResult.Invalid(
        issues.map { snapshotIssue ->
            issue(
                code = GroupRotationMatchPersistenceIssueCode.INVALID_STORED_DATA,
                matchId = matchId,
                lobbySlotNumber = snapshotIssue.lobbySlotNumber,
                teamUUID = snapshotIssue.teamUUID,
            )
        }.ifEmpty {
            listOf(issue(GroupRotationMatchPersistenceIssueCode.INVALID_STORED_DATA, matchId))
        },
    )

    private fun validateMatchAndSnapshot(
        tournament: Tournament,
        matchId: String,
        matchTournamentId: String,
        matchPairingKey: String?,
        snapshot: GroupRotationMatchIdentitySnapshot,
        identities: List<GroupRotationTeamIdentity>,
    ): List<GroupRotationMatchPersistenceIssue> {
        val issues = mutableListOf<GroupRotationMatchPersistenceIssue>()
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.NOT_GROUP_ROTATION, matchId)
        }
        if (snapshot.tournamentId != tournament.id) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.TOURNAMENT_ID_MISMATCH, matchId)
        }
        if (matchTournamentId != tournament.id) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.MATCH_TOURNAMENT_MISMATCH, matchId)
        }
        if (matchPairingKey != snapshot.pairing.canonicalKey) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.MATCH_PAIRING_MISMATCH, matchId)
        }
        if (snapshot.pairing !in tournament.selectedGroupPairings) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.UNSELECTED_PAIRING, matchId)
        }

        val identitiesByUuid = identities.associateBy { it.teamUUID }
        val duplicateLobbies = snapshot.teams.groupBy { it.lobbySlotNumber }
            .filterValues { it.size > 1 }
        duplicateLobbies.keys.forEach { lobbySlot ->
            issues += issue(
                GroupRotationMatchPersistenceIssueCode.DUPLICATE_LOBBY_SLOT,
                matchId,
                lobbySlotNumber = lobbySlot,
            )
        }
        val duplicateUuids = snapshot.teams.groupBy { it.teamUUID }
            .filterValues { it.size > 1 }
        duplicateUuids.keys.forEach { teamUUID ->
            issues += issue(
                GroupRotationMatchPersistenceIssueCode.DUPLICATE_TEAM_UUID,
                matchId,
                teamUUID = teamUUID,
            )
        }
        snapshot.teams.forEach { team ->
            if (team.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.INVALID_LOBBY_SLOT,
                    matchId,
                    team.lobbySlotNumber,
                    team.teamUUID,
                )
            }
            if (team.teamUUID.isBlank()) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.INVALID_TEAM_IDENTITY,
                    matchId,
                    team.lobbySlotNumber,
                    team.teamUUID,
                )
            }
            val identity = identitiesByUuid[team.teamUUID]
            if (identity == null) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.TEAM_UUID_NOT_FOUND,
                    matchId,
                    team.lobbySlotNumber,
                    team.teamUUID,
                )
            } else {
                if (identity.tournamentId != tournament.id) {
                    issues += issue(
                        GroupRotationMatchPersistenceIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                        matchId,
                        team.lobbySlotNumber,
                        team.teamUUID,
                    )
                }
                if (
                    (identity.displayName.isBlank() && team.displayName.isNotBlank()) ||
                        (identity.displayName.isNotBlank() &&
                            identity.normalizedName != normalizer.normalize(identity.displayName))
                ) {
                    issues += issue(
                        GroupRotationMatchPersistenceIssueCode.INVALID_TEAM_IDENTITY,
                        matchId,
                        team.lobbySlotNumber,
                        team.teamUUID,
                    )
                }
                if (identity.displayName != team.displayName) {
                    issues += issue(
                        GroupRotationMatchPersistenceIssueCode.INVALID_TEAM_IDENTITY,
                        matchId,
                        team.lobbySlotNumber,
                        team.teamUUID,
                    )
                }
            }
        }
        return issues.distinct()
    }

    private fun validateStoredHeaderAndAssignments(
        tournament: Tournament,
        matchId: String,
        header: GroupRotationMatchIdentityHeaderEntity,
        assignments: List<GroupRotationMatchTeamAssignmentEntity>,
    ): List<GroupRotationMatchPersistenceIssue> {
        val issues = mutableListOf<GroupRotationMatchPersistenceIssue>()
        if (header.tournamentId != tournament.id) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.TOURNAMENT_ID_MISMATCH, matchId)
        }
        val duplicateLobbies = assignments.groupBy { it.lobbySlotNumber }.filterValues { it.size > 1 }
        if (duplicateLobbies.isNotEmpty()) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.DUPLICATE_LOBBY_SLOT, matchId)
        }
        val duplicateUuids = assignments.groupBy { it.teamUUID }.filterValues { it.size > 1 }
        if (duplicateUuids.isNotEmpty()) {
            issues += issue(GroupRotationMatchPersistenceIssueCode.DUPLICATE_TEAM_UUID, matchId)
        }
        assignments.forEach { assignment ->
            if (assignment.tournamentId != tournament.id) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.TOURNAMENT_ID_MISMATCH,
                    matchId,
                    assignment.lobbySlotNumber,
                    assignment.teamUUID,
                )
            }
            if (assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.INVALID_LOBBY_SLOT,
                    matchId,
                    assignment.lobbySlotNumber,
                    assignment.teamUUID,
                )
            }
        }
        return issues.distinct()
    }

    private fun validateParticipantResults(
        matchId: String,
        results: List<GroupRotationMatchParticipantResult>,
        assignedTeamUuids: Set<String>,
        identitiesByUuid: Map<String, com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity>,
    ): List<GroupRotationMatchPersistenceIssue> = validateParticipantRows(
        matchId = matchId,
        results = results.map { result ->
            ParticipantRow(
                teamUUID = result.teamUUID,
                participationStatus = result.participationStatus.name,
                placement = result.placement,
                kills = result.kills,
            )
        },
        assignedTeamUuids = assignedTeamUuids,
        identitiesByUuid = identitiesByUuid,
    )

    private fun validateParticipantEntities(
        matchId: String,
        results: List<GroupRotationMatchParticipantResultEntity>,
        assignedTeamUuids: Set<String>,
        identitiesByUuid: Map<String, com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity>,
    ): List<GroupRotationMatchPersistenceIssue> = validateParticipantRows(
        matchId = matchId,
        results = results.map { result ->
            ParticipantRow(
                teamUUID = result.teamUUID,
                participationStatus = result.participationStatus,
                placement = result.placement,
                kills = result.kills,
            )
        },
        assignedTeamUuids = assignedTeamUuids,
        identitiesByUuid = identitiesByUuid,
    )

    private fun validateParticipantRows(
        matchId: String,
        results: List<ParticipantRow>,
        assignedTeamUuids: Set<String>,
        identitiesByUuid: Map<String, com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity>,
    ): List<GroupRotationMatchPersistenceIssue> {
        val issues = mutableListOf<GroupRotationMatchPersistenceIssue>()
        results.groupBy { it.teamUUID }.filterValues { it.size > 1 }.keys.forEach { teamUUID ->
            issues += issue(
                GroupRotationMatchPersistenceIssueCode.DUPLICATE_PARTICIPANT_RESULT,
                matchId,
                teamUUID = teamUUID,
            )
        }
        results.groupBy { it.placement }.filterKeys { it != null }.filterValues { it.size > 1 }
            .keys.forEach { placement ->
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.DUPLICATE_PLACEMENT,
                    matchId,
                )
            }
        results.forEach { result ->
            if (result.teamUUID !in assignedTeamUuids) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.PARTICIPANT_TEAM_NOT_IN_MATCH,
                    matchId,
                    teamUUID = result.teamUUID,
                )
            }
            val identity = identitiesByUuid[result.teamUUID]
            if (identity == null) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.TEAM_UUID_NOT_FOUND,
                    matchId,
                    teamUUID = result.teamUUID,
                )
            } else if (identity.tournamentId.isBlank()) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                    matchId,
                    teamUUID = result.teamUUID,
                )
            }
            if (result.kills < 0) {
                issues += issue(
                    GroupRotationMatchPersistenceIssueCode.INVALID_KILLS,
                    matchId,
                    teamUUID = result.teamUUID,
                )
            }
            when (result.participationStatus) {
                MatchParticipationStatus.PARTICIPATED.name -> {
                    if (result.placement != null && result.placement !in 1..12) {
                        issues += issue(
                            GroupRotationMatchPersistenceIssueCode.INVALID_PLACEMENT,
                            matchId,
                            teamUUID = result.teamUUID,
                        )
                    }
                }
                MatchParticipationStatus.NO_SHOW.name -> {
                    if (result.placement != null || result.kills != 0) {
                        issues += issue(
                            GroupRotationMatchPersistenceIssueCode.INVALID_NO_SHOW_RESULT,
                            matchId,
                            teamUUID = result.teamUUID,
                        )
                    }
                }
                else -> issues += issue(
                    GroupRotationMatchPersistenceIssueCode.INVALID_PARTICIPATION_STATUS,
                    matchId,
                    teamUUID = result.teamUUID,
                )
            }
        }
        return issues.distinct()
    }

    private fun currentIdentitySnapshot(
        snapshot: GroupRotationMatchIdentitySnapshot,
        identitiesByUuid: Map<String, com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity>,
    ): GroupRotationMatchIdentitySnapshot = snapshot.copy(
        teams = snapshot.teams.sortedBy { it.lobbySlotNumber }.map { team ->
            val identity = identitiesByUuid.getValue(team.teamUUID)
            team.copy(displayName = identity.displayName)
        },
    )

    private fun com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity.toDomain() =
        GroupRotationTeamIdentity(
            tournamentId = tournamentId,
            teamUUID = teamUUID,
            displayName = displayName,
            normalizedName = normalizedName,
        )

    private fun invalid(
        code: GroupRotationMatchPersistenceIssueCode,
        matchId: String? = null,
        lobbySlotNumber: Int? = null,
        teamUUID: String? = null,
    ) = GroupRotationMatchPersistenceResult.Invalid(
        listOf(issue(code, matchId, lobbySlotNumber, teamUUID)),
    )

    private fun issue(
        code: GroupRotationMatchPersistenceIssueCode,
        matchId: String? = null,
        lobbySlotNumber: Int? = null,
        teamUUID: String? = null,
    ) = GroupRotationMatchPersistenceIssue(code, matchId, lobbySlotNumber, teamUUID)

    private data class ParticipantRow(
        val teamUUID: String,
        val participationStatus: String,
        val placement: Int?,
        val kills: Int,
    )
}
