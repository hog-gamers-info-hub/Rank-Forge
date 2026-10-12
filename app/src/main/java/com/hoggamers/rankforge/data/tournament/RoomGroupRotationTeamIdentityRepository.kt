package com.hoggamers.rankforge.data.tournament

import androidx.room.withTransaction
import com.hoggamers.rankforge.data.cloud.TournamentCloudIdentity
import com.hoggamers.rankforge.data.local.GroupRotationPairingTeamIdentityAssignmentEntity
import com.hoggamers.rankforge.data.local.GroupRotationTeamIdentityEntity
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityResolver
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityPlanResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamRenameResult
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import java.util.UUID
import javax.inject.Inject

class RoomGroupRotationTeamIdentityRepository @Inject constructor(
    private val database: RankForgeDatabase,
) : GroupRotationTeamIdentityLocalRepository {
    private val resolver = GroupRotationTeamIdentityResolver()

    override suspend fun read(
        tournament: Tournament,
    ): GroupRotationTeamIdentityReadResult = database.withTransaction {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return@withTransaction GroupRotationTeamIdentityReadResult.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.NOT_GROUP_ROTATION)),
            )
        }
        when (val backfill = ensureLegacyIdentities(tournament)) {
            is LegacyIdentityBackfill.Invalid -> {
                GroupRotationTeamIdentityReadResult.Invalid(backfill.issues)
            }
            is LegacyIdentityBackfill.Ready -> {
                when (val merged = mergeLegacyAssignments(tournament, backfill.teams)) {
                    is LegacyAssignmentMerge.Invalid ->
                        return@withTransaction GroupRotationTeamIdentityReadResult.Invalid(merged.issues)
                    is LegacyAssignmentMerge.Ready -> {
                        validateStoredState(tournament, backfill.teams, merged.assignments)?.let { issues ->
                            return@withTransaction GroupRotationTeamIdentityReadResult.Invalid(issues)
                        }
                        persist(backfill.teams, merged.assignments)
                        return@withTransaction GroupRotationTeamIdentityReadResult.Loaded(
                            GroupRotationTeamIdentityLocalSnapshot(backfill.teams, merged.assignments),
                        )
                    }
                }
            }
        }
    }

    override suspend fun savePairing(
        tournament: Tournament,
        pairingKey: GroupPairing,
        entries: List<GroupRotationPairingTeamIdentityEntry>,
    ): GroupRotationTeamIdentityWriteResult = database.withTransaction {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return@withTransaction invalid(GroupRotationTeamIdentityIssueCode.NOT_GROUP_ROTATION)
        }
        when (val backfill = ensureLegacyIdentities(tournament)) {
            is LegacyIdentityBackfill.Invalid -> return@withTransaction invalid(backfill.issues)
            is LegacyIdentityBackfill.Ready -> {
                when (val merged = mergeLegacyAssignments(tournament, backfill.teams)) {
                    is LegacyAssignmentMerge.Invalid -> return@withTransaction invalid(merged.issues)
                    is LegacyAssignmentMerge.Ready -> when (
                    val planned = resolver.planPairing(
                        tournament = tournament,
                        pairingKey = pairingKey,
                        entries = entries,
                        existingTeams = backfill.teams,
                        existingAssignments = merged.assignments,
                    )
                ) {
                    is GroupRotationTeamIdentityPlanResult.Invalid -> {
                        GroupRotationTeamIdentityWriteResult.Invalid(planned.issues)
                    }
                    is GroupRotationTeamIdentityPlanResult.Planned -> {
                        val existingPairingAssignments = merged.assignments
                            .filter { it.pairingKey == pairingKey }
                        val plannedPairingAssignments = planned.plan.assignments
                            .filter { it.pairingKey == pairingKey }
                        if (
                            database.matchDao().readForTournamentId(tournament.id)
                                .any { it.groupPairingKey == pairingKey.canonicalKey } &&
                            existingPairingAssignments != plannedPairingAssignments
                        ) {
                            return@withTransaction GroupRotationTeamIdentityWriteResult.ProtectedHistory
                        }
                        persist(planned.plan.teams, planned.plan.assignments)
                        GroupRotationTeamIdentityWriteResult.Saved(
                            GroupRotationTeamIdentityLocalSnapshot(
                                planned.plan.teams,
                                planned.plan.assignments,
                            ),
                        )
                    }
                }
                }
            }
        }
    }

    override suspend fun renameTeam(
        tournament: Tournament,
        teamUUID: String,
        displayName: String,
    ): GroupRotationTeamIdentityWriteResult = database.withTransaction {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return@withTransaction invalid(GroupRotationTeamIdentityIssueCode.NOT_GROUP_ROTATION)
        }
        when (val backfill = ensureLegacyIdentities(tournament)) {
            is LegacyIdentityBackfill.Invalid -> return@withTransaction invalid(backfill.issues)
            is LegacyIdentityBackfill.Ready -> {
                when (val merged = mergeLegacyAssignments(tournament, backfill.teams)) {
                    is LegacyAssignmentMerge.Invalid -> return@withTransaction invalid(merged.issues)
                    is LegacyAssignmentMerge.Ready -> when (
                    val renamed = resolver.renameTeam(
                        tournament = tournament,
                        teamUUID = teamUUID,
                        displayName = displayName,
                        existingTeams = backfill.teams,
                        existingAssignments = merged.assignments,
                    )
                ) {
                    is GroupRotationTeamRenameResult.Invalid -> {
                        GroupRotationTeamIdentityWriteResult.Invalid(renamed.issues)
                    }
                    is GroupRotationTeamRenameResult.Renamed -> {
                        persist(renamed.teams, renamed.assignments)
                        GroupRotationTeamIdentityWriteResult.Saved(
                            GroupRotationTeamIdentityLocalSnapshot(
                                renamed.teams,
                                renamed.assignments,
                            ),
                        )
                    }
                }
                }
            }
        }
    }

    private suspend fun persist(
        teams: List<GroupRotationTeamIdentity>,
        assignments: List<GroupRotationPairingAssignment>,
    ) {
        database.groupRotationTeamIdentityDao().upsertAll(teams.map { it.toEntity() })
        database.groupRotationPairingTeamIdentityAssignmentDao().upsertAll(
            assignments.map { it.toEntity() },
        )
    }

    private suspend fun ensureLegacyIdentities(
        tournament: Tournament,
    ): LegacyIdentityBackfill {
        val existingEntities = database.groupRotationTeamIdentityDao()
            .readByTournamentId(tournament.id)
        val existingTeams = existingEntities.map { it.toDomain() }
        val normalizer = com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer()
        val issues = mutableListOf<GroupRotationTeamIdentityIssue>()
        val pending = mutableListOf<GroupRotationTeamIdentity>()
        val capacity = when (tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> 0
        }
        val normalizedOwners = existingTeams.groupBy { it.normalizedName }
        normalizedOwners.filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }
            .keys
            .forEach { normalizedName ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                    normalizedName = normalizedName,
                )
            }

        val tournamentUuid = runCatching { UUID.fromString(tournament.id) }.getOrNull()
        if (tournamentUuid == null && database.teamSlotDao().readByTournamentId(tournament.id)
                .any { it.teamName.trim().isNotEmpty() }
        ) {
            issues += GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.LEGACY_IDENTITY_UNAVAILABLE)
        } else if (tournamentUuid != null) {
            val existingByUuid = existingTeams.associateBy { it.teamUUID }
            val occupiedSlots = database.teamSlotDao().readByTournamentId(tournament.id)
                .filter { it.teamName.trim().isNotEmpty() }
            occupiedSlots
                .filter { it.slotNumber !in 1..capacity }
                .forEach {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED,
                    )
                }
            occupiedSlots.forEach { slot ->
                val normalizedName = normalizer.normalize(slot.teamName)
                val legacyUuid = TournamentCloudIdentity.teamSlotId(tournamentUuid, slot.slotNumber)
                val existing = existingByUuid[legacyUuid]
                if (existing == null) {
                    val collision = (existingTeams + pending).firstOrNull {
                        it.normalizedName == normalizedName
                    }
                    if (collision != null) {
                        issues += GroupRotationTeamIdentityIssue(
                            GroupRotationTeamIdentityIssueCode.TEAM_NAME_COLLISION,
                            teamUUID = legacyUuid,
                            normalizedName = normalizedName,
                        )
                    } else {
                        pending += GroupRotationTeamIdentity(
                            tournamentId = tournament.id,
                            teamUUID = legacyUuid,
                            displayName = normalizer.cleanDisplayName(slot.teamName),
                            normalizedName = normalizedName,
                        )
                    }
                }
            }
        }
        if (issues.isNotEmpty()) return LegacyIdentityBackfill.Invalid(issues.distinct())
        return LegacyIdentityBackfill.Ready(existingTeams + pending)
    }

    private suspend fun mergeLegacyAssignments(
        tournament: Tournament,
        teams: List<GroupRotationTeamIdentity>,
    ): LegacyAssignmentMerge {
        val storedAssignments = readStoredAssignments(tournament.id)
            ?: return LegacyAssignmentMerge.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.INVALID_PAIRING_KEY)),
            )
        val legacyRows = database.groupRotationPairingLobbySlotDao()
            .readByTournamentId(tournament.id)
        if (legacyRows.isEmpty()) return LegacyAssignmentMerge.Ready(storedAssignments)

        val storedPairings = storedAssignments.map { it.pairingKey }.toSet()
        val teamSlotsByNumber = database.teamSlotDao()
            .readByTournamentId(tournament.id)
            .associateBy { it.slotNumber }
        val teamsByUuid = teams.associateBy { it.teamUUID }
        val teamsByNormalizedName = teams.groupBy { it.normalizedName }
        val issues = mutableListOf<GroupRotationTeamIdentityIssue>()
        val imported = storedAssignments.toMutableList()

        legacyRows
            .groupBy { it.pairingKey }
            .filterKeys { key ->
                runCatching { GroupPairing.fromCanonicalKey(key) }.getOrNull() !in storedPairings
            }
            .forEach { (pairingKey, rows) ->
                val pairing = runCatching { GroupPairing.fromCanonicalKey(pairingKey) }.getOrNull()
                if (pairing == null) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.INVALID_PAIRING_KEY,
                        pairingKey = pairingKey,
                    )
                    return@forEach
                }
                if (pairing !in tournament.selectedGroupPairings) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.UNSELECTED_PAIRING,
                        pairingKey = pairingKey,
                    )
                    return@forEach
                }
                rows.forEach { row ->
                    if (row.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                        issues += GroupRotationTeamIdentityIssue(
                            GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT,
                            pairingKey = pairingKey,
                            lobbySlotNumber = row.lobbySlotNumber,
                        )
                        return@forEach
                    }
                    val slot = teamSlotsByNumber[row.teamSlotNumber]
                    if (slot == null || slot.teamName.trim().isEmpty()) {
                        issues += GroupRotationTeamIdentityIssue(
                            GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND,
                            pairingKey = pairingKey,
                            lobbySlotNumber = row.lobbySlotNumber,
                        )
                        return@forEach
                    }
                    val legacyUuid = runCatching {
                        TournamentCloudIdentity.teamSlotId(UUID.fromString(tournament.id), row.teamSlotNumber)
                    }.getOrNull()
                    val normalizedName = com.hoggamers.rankforge.domain.tournament
                        .GroupRotationTeamIdentityNormalizer()
                        .normalize(slot.teamName)
                    val identity = legacyUuid?.let(teamsByUuid::get)
                        ?: teamsByNormalizedName[normalizedName]?.singleOrNull()
                    if (identity == null) {
                        issues += GroupRotationTeamIdentityIssue(
                            GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND,
                            pairingKey = pairingKey,
                            lobbySlotNumber = row.lobbySlotNumber,
                        )
                        return@forEach
                    }
                    imported += GroupRotationPairingAssignment(
                        tournamentId = tournament.id,
                        pairingKey = pairing,
                        lobbySlotNumber = row.lobbySlotNumber,
                        teamUUID = identity.teamUUID,
                    )
                }
            }

        if (issues.isNotEmpty()) return LegacyAssignmentMerge.Invalid(issues.distinct())
        val result = imported.distinct().sortedWith(
            compareBy<GroupRotationPairingAssignment> { it.pairingKey.canonicalKey }
                .thenBy { it.lobbySlotNumber },
        )
        return LegacyAssignmentMerge.Ready(result)
    }

    private suspend fun readStoredAssignments(
        tournamentId: String,
    ): List<GroupRotationPairingAssignment>? = database
        .groupRotationPairingTeamIdentityAssignmentDao()
        .readByTournamentId(tournamentId)
        .map { entity ->
            val pairing = runCatching { GroupPairing.fromCanonicalKey(entity.pairingKey) }.getOrNull()
                ?: return null
            GroupRotationPairingAssignment(
                tournamentId = entity.tournamentId,
                pairingKey = pairing,
                lobbySlotNumber = entity.lobbySlotNumber,
                teamUUID = entity.teamUUID,
            )
        }

    private fun validateStoredState(
        tournament: Tournament,
        teams: List<GroupRotationTeamIdentity>,
        assignments: List<GroupRotationPairingAssignment>,
    ): List<GroupRotationTeamIdentityIssue>? {
        val firstPairing = tournament.selectedGroupPairings.firstOrNull()
            ?: return listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.UNSELECTED_PAIRING))
        return when (
            val result = resolver.planPairing(
                tournament = tournament,
                pairingKey = firstPairing,
                entries = emptyList(),
                existingTeams = teams,
                existingAssignments = assignments,
            )
        ) {
            is GroupRotationTeamIdentityPlanResult.Invalid -> result.issues
            is GroupRotationTeamIdentityPlanResult.Planned -> null
        }
    }

    private fun invalid(
        code: GroupRotationTeamIdentityIssueCode,
    ): GroupRotationTeamIdentityWriteResult.Invalid =
        GroupRotationTeamIdentityWriteResult.Invalid(listOf(GroupRotationTeamIdentityIssue(code)))

    private fun invalid(
        issues: List<GroupRotationTeamIdentityIssue>,
    ): GroupRotationTeamIdentityWriteResult.Invalid =
        GroupRotationTeamIdentityWriteResult.Invalid(issues)

    private sealed interface LegacyIdentityBackfill {
        data class Ready(val teams: List<GroupRotationTeamIdentity>) : LegacyIdentityBackfill

        data class Invalid(val issues: List<GroupRotationTeamIdentityIssue>) : LegacyIdentityBackfill
    }

    private sealed interface LegacyAssignmentMerge {
        data class Ready(val assignments: List<GroupRotationPairingAssignment>) : LegacyAssignmentMerge

        data class Invalid(val issues: List<GroupRotationTeamIdentityIssue>) : LegacyAssignmentMerge
    }
}

private fun GroupRotationTeamIdentityEntity.toDomain(): GroupRotationTeamIdentity =
    GroupRotationTeamIdentity(
        tournamentId = tournamentId,
        teamUUID = teamUUID,
        displayName = displayName,
        normalizedName = normalizedName,
    )

private fun GroupRotationTeamIdentity.toEntity(): GroupRotationTeamIdentityEntity =
    GroupRotationTeamIdentityEntity(
        tournamentId = tournamentId,
        teamUUID = teamUUID,
        displayName = displayName,
        normalizedName = normalizedName,
    )

private fun GroupRotationPairingAssignment.toEntity(): GroupRotationPairingTeamIdentityAssignmentEntity =
    GroupRotationPairingTeamIdentityAssignmentEntity(
        tournamentId = tournamentId,
        pairingKey = pairingKey.canonicalKey,
        lobbySlotNumber = lobbySlotNumber,
        teamUUID = teamUUID,
    )
