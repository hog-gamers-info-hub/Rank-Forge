package com.hoggamers.rankforge.domain.tournament

import java.util.UUID

/** Tournament-scoped Group Rotation team identity. */
data class GroupRotationTeamIdentity(
    val tournamentId: String,
    val teamUUID: String,
    val displayName: String,
    val normalizedName: String,
)

/** Pairing-local lobby assignment to a tournament-scoped team identity. */
data class GroupRotationPairingAssignment(
    val tournamentId: String,
    val pairingKey: GroupPairing,
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val nameCleared: Boolean = false,
)

/** A reviewed pairing entry. A null UUID means that the team is unassigned. */
data class GroupRotationPairingTeamIdentityEntry(
    val lobbySlotNumber: Int,
    val displayName: String,
    val teamUUID: String? = null,
)

enum class GroupRotationTeamIdentityIssueCode {
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    BLANK_TEAM_NAME,
    INVALID_REGISTERED_TEAM,
    DUPLICATE_TEAM_UUID,
    DUPLICATE_NORMALIZED_NAME,
    TEAM_CAPACITY_EXCEEDED,
    UNSELECTED_PAIRING,
    INVALID_LOBBY_SLOT,
    INVALID_PAIRING_ENTRY_COUNT,
    DUPLICATE_LOBBY_SLOT,
    MISSING_LOBBY_SLOT,
    DUPLICATE_TEAM_IN_PAIRING,
    TEAM_UUID_NOT_FOUND,
    TEAM_UUID_TOURNAMENT_MISMATCH,
    INVALID_TEAM_UUID,
    TEAM_NAME_COLLISION,
    ASSIGNED_TEAM_UUID_CONFLICT,
    IMPLICIT_ASSIGNMENT_CLEAR,
    UNASSIGNED_SLOT_WITH_UUID,
    UNASSIGNED_UUID_NAME_MISMATCH,
    UUID_GENERATION_FAILED,
    INVALID_PAIRING_KEY,
    LEGACY_IDENTITY_UNAVAILABLE,
}

data class GroupRotationTeamIdentityIssue(
    val code: GroupRotationTeamIdentityIssueCode,
    val teamUUID: String? = null,
    val normalizedName: String? = null,
    val lobbySlotNumber: Int? = null,
    val pairingKey: String? = null,
)

data class GroupRotationTeamIdentityPlan(
    val teams: List<GroupRotationTeamIdentity>,
    val assignments: List<GroupRotationPairingAssignment>,
)

fun interface GroupRotationTeamUuidGenerator {
    fun generate(): String
}

sealed interface GroupRotationTeamRegistrationResult {
    data class Registered(
        val identity: GroupRotationTeamIdentity,
        val teams: List<GroupRotationTeamIdentity>,
    ) : GroupRotationTeamRegistrationResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityIssue>,
    ) : GroupRotationTeamRegistrationResult
}

sealed interface GroupRotationTeamIdentityPlanResult {
    data class Planned(
        val plan: GroupRotationTeamIdentityPlan,
    ) : GroupRotationTeamIdentityPlanResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityIssue>,
    ) : GroupRotationTeamIdentityPlanResult
}

sealed interface GroupRotationTeamRenameResult {
    data class Renamed(
        val identity: GroupRotationTeamIdentity,
        val teams: List<GroupRotationTeamIdentity>,
        val assignments: List<GroupRotationPairingAssignment>,
    ) : GroupRotationTeamRenameResult

    data class Invalid(
        val issues: List<GroupRotationTeamIdentityIssue>,
    ) : GroupRotationTeamRenameResult
}

/**
 * Pure identity planning for Group Rotation. It deliberately has no TeamSlot
 * or numeric permanent-slot dependency.
 */
class GroupRotationTeamIdentityResolver(
    private val uuidGenerator: GroupRotationTeamUuidGenerator =
        GroupRotationTeamUuidGenerator { UUID.randomUUID().toString() },
    private val normalizer: GroupRotationTeamIdentityNormalizer =
        GroupRotationTeamIdentityNormalizer(),
) {
    fun registerUnassignedTeam(
        tournament: Tournament,
        displayName: String,
        existingTeams: List<GroupRotationTeamIdentity>,
        existingAssignments: List<GroupRotationPairingAssignment> = emptyList(),
    ): GroupRotationTeamRegistrationResult {
        val issues = validateTournamentAndTeams(tournament, existingTeams, existingAssignments)
        if (issues.isNotEmpty()) return GroupRotationTeamRegistrationResult.Invalid(issues)

        val cleanedName = normalizer.cleanDisplayName(displayName)
        if (cleanedName.isBlank()) {
            return GroupRotationTeamRegistrationResult.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.BLANK_TEAM_NAME)),
            )
        }
        val normalizedName = normalizer.normalize(cleanedName)
        val activeTeamUUIDs = existingAssignments
            .filterNot { it.nameCleared }
            .map { it.teamUUID }
            .toSet()
        val assignedTeamUUIDs = existingAssignments.map { it.teamUUID }.toSet()
        existingTeams.firstOrNull {
            it.normalizedName == normalizedName &&
                (it.teamUUID in activeTeamUUIDs ||
                    (it.teamUUID !in assignedTeamUUIDs && it.normalizedName.isNotBlank()))
        }?.let { existing ->
            return GroupRotationTeamRegistrationResult.Registered(existing, existingTeams)
        }
        if (existingTeams.size >= capacityFor(tournament)) {
            return GroupRotationTeamRegistrationResult.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED)),
            )
        }

        val teamUUID = uuidGenerator.generate()
        if (!isCanonicalUuid(teamUUID) || existingTeams.any { it.teamUUID == teamUUID }) {
            return GroupRotationTeamRegistrationResult.Invalid(
                listOf(
                    GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.UUID_GENERATION_FAILED,
                        teamUUID = teamUUID,
                    ),
                ),
            )
        }
        val identity = GroupRotationTeamIdentity(
            tournamentId = tournament.id,
            teamUUID = teamUUID,
            displayName = cleanedName,
            normalizedName = normalizedName,
        )
        return GroupRotationTeamRegistrationResult.Registered(identity, existingTeams + identity)
    }

    fun planPairing(
        tournament: Tournament,
        pairingKey: GroupPairing,
        entries: List<GroupRotationPairingTeamIdentityEntry>,
        existingTeams: List<GroupRotationTeamIdentity>,
        existingAssignments: List<GroupRotationPairingAssignment>,
    ): GroupRotationTeamIdentityPlanResult = planPairings(
        tournament = tournament,
        entriesByPairing = mapOf(pairingKey to entries),
        existingTeams = existingTeams,
        existingAssignments = existingAssignments,
    )

    fun planPairings(
        tournament: Tournament,
        entriesByPairing: Map<GroupPairing, List<GroupRotationPairingTeamIdentityEntry>>,
        existingTeams: List<GroupRotationTeamIdentity>,
        existingAssignments: List<GroupRotationPairingAssignment>,
    ): GroupRotationTeamIdentityPlanResult {
        val issues = (
            validateTournamentAndTeams(tournament, existingTeams, existingAssignments) +
                validateAssignments(tournament, existingTeams, existingAssignments) +
                if (tournament.selectedGroupPairings.isEmpty()) {
                    listOf(
                        GroupRotationTeamIdentityIssue(
                            GroupRotationTeamIdentityIssueCode.UNSELECTED_PAIRING,
                        ),
                    )
                } else {
                    emptyList()
                } +
                entriesByPairing.entries.flatMap { (pairingKey, entries) ->
                    validatePairing(pairingKey, tournament) +
                        validateLobbyShape(pairingKey, entries) +
                        duplicateEntryNameIssues(pairingKey, entries) +
                        if (entries.size > GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS.count()) {
                            listOf(
                                GroupRotationTeamIdentityIssue(
                                    GroupRotationTeamIdentityIssueCode.INVALID_PAIRING_ENTRY_COUNT,
                                    pairingKey = pairingKey.canonicalKey,
                                ),
                            )
                        } else {
                            emptyList()
                        }
                }
            ).toMutableList()
        if (issues.isNotEmpty()) return GroupRotationTeamIdentityPlanResult.Invalid(issues.distinct())

        var teams = existingTeams
        val explicitEntriesByAssignment = entriesByPairing
            .flatMap { (pairingKey, entries) ->
                entries.map { entry -> (pairingKey to entry.lobbySlotNumber) to entry }
            }
            .toMap()
        val plannedAssignments = existingAssignments.map { assignment ->
            val entry = explicitEntriesByAssignment[assignment.pairingKey to assignment.lobbySlotNumber]
            when {
                entry == null -> assignment
                normalizer.cleanDisplayName(entry.displayName).isBlank() -> assignment.copy(nameCleared = true)
                else -> assignment.copy(nameCleared = false)
            }
        }.toMutableList()
        val proposedNamesByUuid = linkedMapOf<String, String>()
        val previouslyAssignedUuids = existingAssignments.map { it.teamUUID }.toSet()

        fun replaceAssignment(assignment: GroupRotationPairingAssignment) {
            val index = plannedAssignments.indexOfFirst {
                it.pairingKey == assignment.pairingKey &&
                    it.lobbySlotNumber == assignment.lobbySlotNumber
            }
            if (index >= 0) {
                plannedAssignments[index] = assignment
            } else {
                plannedAssignments += assignment
            }
        }

        entriesByPairing.entries
            .sortedBy { it.key.canonicalKey }
            .forEach { (pairingKey, entries) ->
                val existingAssignmentByLobby = existingAssignments
                    .filter { it.pairingKey == pairingKey }
                    .associateBy { it.lobbySlotNumber }
                entries.sortedBy { it.lobbySlotNumber }.forEach { entry ->
            val cleanedDisplayName = normalizer.cleanDisplayName(entry.displayName)
            val normalizedName = normalizer.normalize(cleanedDisplayName)
            val savedAssignment = existingAssignmentByLobby[entry.lobbySlotNumber]
            var resolved: GroupRotationTeamIdentity? = null

            if (savedAssignment != null) {
                if (entry.teamUUID != null && entry.teamUUID != savedAssignment.teamUUID) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.ASSIGNED_TEAM_UUID_CONFLICT,
                        teamUUID = entry.teamUUID,
                        lobbySlotNumber = entry.lobbySlotNumber,
                        pairingKey = pairingKey.canonicalKey,
                    )
                    return@forEach
                }
                val savedTeam = teams.firstOrNull { it.teamUUID == savedAssignment.teamUUID }
                if (savedTeam == null) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND,
                        teamUUID = savedAssignment.teamUUID,
                        lobbySlotNumber = entry.lobbySlotNumber,
                        pairingKey = pairingKey.canonicalKey,
                    )
                    return@forEach
                }
                if (cleanedDisplayName.isBlank()) {
                    replaceAssignment(savedAssignment.copy(nameCleared = true))
                    return@forEach
                }
                resolved = savedTeam
            } else {
                if (entry.displayName.isBlank() && entry.teamUUID != null) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.UNASSIGNED_SLOT_WITH_UUID,
                        teamUUID = entry.teamUUID,
                        lobbySlotNumber = entry.lobbySlotNumber,
                        pairingKey = pairingKey.canonicalKey,
                    )
                    return@forEach
                }
                if (entry.displayName.isBlank()) {
                    resolved = null
                    return@forEach
                }

                if (entry.teamUUID == null) {
                    val activeUuids = plannedAssignments
                        .filterNot { it.nameCleared }
                        .map { it.teamUUID }
                        .toSet()
                    val existingAssignmentUuids = existingAssignments.map { it.teamUUID }.toSet()
                    val activeMatch = teams.firstOrNull {
                        it.normalizedName == normalizedName &&
                            (it.teamUUID in activeUuids ||
                                (it.teamUUID !in existingAssignmentUuids && it.normalizedName.isNotBlank()))
                    }
                    resolved = activeMatch ?: when (
                        val result = registerUnassignedTeam(
                            tournament,
                            entry.displayName,
                            teams,
                            plannedAssignments,
                        )
                    ) {
                        is GroupRotationTeamRegistrationResult.Registered -> {
                            teams = result.teams
                            result.identity
                        }

                        is GroupRotationTeamRegistrationResult.Invalid -> {
                            issues += result.issues
                            null
                        }
                    }
                } else {
                    val existing = teams.firstOrNull { it.teamUUID == entry.teamUUID }
                    when {
                        existing == null -> {
                            issues += GroupRotationTeamIdentityIssue(
                                GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND,
                                teamUUID = entry.teamUUID,
                                lobbySlotNumber = entry.lobbySlotNumber,
                                pairingKey = pairingKey.canonicalKey,
                            )
                            resolved = null
                        }
                        existing.tournamentId != tournament.id -> {
                            issues += GroupRotationTeamIdentityIssue(
                                GroupRotationTeamIdentityIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                                teamUUID = entry.teamUUID,
                                lobbySlotNumber = entry.lobbySlotNumber,
                                pairingKey = pairingKey.canonicalKey,
                            )
                            resolved = null
                        }
                        existing.normalizedName != normalizedName -> {
                            issues += GroupRotationTeamIdentityIssue(
                                GroupRotationTeamIdentityIssueCode.UNASSIGNED_UUID_NAME_MISMATCH,
                                teamUUID = entry.teamUUID,
                                normalizedName = normalizedName,
                                lobbySlotNumber = entry.lobbySlotNumber,
                                pairingKey = pairingKey.canonicalKey,
                            )
                            resolved = null
                        }
                        else -> resolved = existing
                    }
                }
            }
            if (resolved != null && cleanedDisplayName.isNotBlank()) {
                val previousName = proposedNamesByUuid[resolved.teamUUID]
                if (previousName != null && previousName != cleanedDisplayName) {
                    issues += GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.TEAM_NAME_COLLISION,
                        teamUUID = resolved.teamUUID,
                        normalizedName = normalizedName,
                        pairingKey = pairingKey.canonicalKey,
                        lobbySlotNumber = entry.lobbySlotNumber,
                    )
                } else {
                    proposedNamesByUuid[resolved.teamUUID] = cleanedDisplayName
                }
                replaceAssignment(
                    GroupRotationPairingAssignment(
                    tournamentId = tournament.id,
                    pairingKey = pairingKey,
                    lobbySlotNumber = entry.lobbySlotNumber,
                    teamUUID = resolved.teamUUID,
                        nameCleared = false,
                    ),
                )
            }
            }
        }

        val finalActiveUuids = plannedAssignments.filterNot { it.nameCleared }.map { it.teamUUID }.toSet()
        teams = teams.map { team ->
            when {
                team.teamUUID in finalActiveUuids -> {
                    val displayName = proposedNamesByUuid[team.teamUUID] ?: team.displayName
                    val cleanedName = normalizer.cleanDisplayName(displayName)
                    team.copy(
                        displayName = cleanedName,
                        normalizedName = normalizer.normalize(cleanedName),
                    )
                }
                team.teamUUID in previouslyAssignedUuids -> team.copy(displayName = "", normalizedName = "")
                else -> team
            }
        }
        issues += validateAssignments(tournament, teams, plannedAssignments)
        issues += validateTournamentAndTeams(tournament, teams, plannedAssignments)
        if (issues.isNotEmpty()) return GroupRotationTeamIdentityPlanResult.Invalid(issues.distinct())

        return GroupRotationTeamIdentityPlanResult.Planned(
            GroupRotationTeamIdentityPlan(
                teams = teams,
                assignments = plannedAssignments
                    .sortedWith(
                        compareBy<GroupRotationPairingAssignment> { it.pairingKey.canonicalKey }
                            .thenBy { it.lobbySlotNumber },
                    ),
            ),
        )
    }

    fun renameTeam(
        tournament: Tournament,
        teamUUID: String,
        displayName: String,
        existingTeams: List<GroupRotationTeamIdentity>,
        existingAssignments: List<GroupRotationPairingAssignment>,
    ): GroupRotationTeamRenameResult {
        val issues = validateTournamentAndTeams(tournament, existingTeams, existingAssignments) +
            validateAssignments(tournament, existingTeams, existingAssignments)
        if (issues.isNotEmpty()) return GroupRotationTeamRenameResult.Invalid(issues)
        return renameTeamInternal(tournament, teamUUID, displayName, existingTeams, existingAssignments)
    }

    private fun renameTeamInternal(
        tournament: Tournament,
        teamUUID: String,
        displayName: String,
        existingTeams: List<GroupRotationTeamIdentity>,
        existingAssignments: List<GroupRotationPairingAssignment> = emptyList(),
    ): GroupRotationTeamRenameResult {
        val identity = existingTeams.firstOrNull { it.teamUUID == teamUUID }
            ?: return GroupRotationTeamRenameResult.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND, teamUUID)),
            )
        if (identity.tournamentId != tournament.id) {
            return GroupRotationTeamRenameResult.Invalid(
                listOf(
                    GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                        teamUUID = teamUUID,
                    ),
                ),
            )
        }
        val cleanedName = normalizer.cleanDisplayName(displayName)
        if (cleanedName.isBlank()) {
            return GroupRotationTeamRenameResult.Invalid(
                listOf(GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.BLANK_TEAM_NAME, teamUUID)),
            )
        }
        val normalizedName = normalizer.normalize(cleanedName)
        if (existingTeams.any { it.teamUUID != teamUUID && it.normalizedName == normalizedName }) {
            return GroupRotationTeamRenameResult.Invalid(
                listOf(
                    GroupRotationTeamIdentityIssue(
                        GroupRotationTeamIdentityIssueCode.TEAM_NAME_COLLISION,
                        teamUUID = teamUUID,
                        normalizedName = normalizedName,
                    ),
                ),
            )
        }
        val renamed = identity.copy(displayName = cleanedName, normalizedName = normalizedName)
        return GroupRotationTeamRenameResult.Renamed(
            renamed,
            existingTeams.map { if (it.teamUUID == teamUUID) renamed else it },
            existingAssignments,
        )
    }

    private fun validateTournamentAndTeams(
        tournament: Tournament,
        teams: List<GroupRotationTeamIdentity>,
        assignments: List<GroupRotationPairingAssignment> = emptyList(),
    ): List<GroupRotationTeamIdentityIssue> {
        val issues = mutableListOf<GroupRotationTeamIdentityIssue>()
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.NOT_GROUP_ROTATION)
        }
        teams.filter { it.tournamentId != tournament.id }.forEach { team ->
            issues += GroupRotationTeamIdentityIssue(
                GroupRotationTeamIdentityIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH,
                teamUUID = team.teamUUID,
            )
        }
        val activeTeamUUIDs = assignments.filterNot { it.nameCleared }.map { it.teamUUID }.toSet()
        teams.forEach { team ->
            val normalized = normalizer.normalize(team.displayName)
            if (!isCanonicalUuid(team.teamUUID)) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.INVALID_TEAM_UUID,
                    teamUUID = team.teamUUID,
                )
            }
            val cleanedName = normalizer.cleanDisplayName(team.displayName)
            if (
                (cleanedName.isBlank() &&
                    (team.normalizedName.isNotBlank() || team.teamUUID in activeTeamUUIDs)) ||
                    (cleanedName.isNotBlank() &&
                        (team.normalizedName.isBlank() || team.normalizedName != normalized))
            ) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.INVALID_REGISTERED_TEAM,
                    teamUUID = team.teamUUID,
                )
            }
        }
        teams.groupBy { it.teamUUID }.filterKeys { it.isNotBlank() }.filterValues { it.size > 1 }
            .keys.forEach { teamUUID ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.DUPLICATE_TEAM_UUID,
                    teamUUID = teamUUID,
                )
            }
        teams.groupBy { it.normalizedName }.filterKeys { it.isNotBlank() }.filterValues { it.size > 1 }
            .keys.forEach { normalizedName ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                    normalizedName = normalizedName,
                )
            }
        if (teams.size > capacityFor(tournament)) {
            issues += GroupRotationTeamIdentityIssue(GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED)
        }
        return issues
    }

    private fun validateAssignments(
        tournament: Tournament,
        teams: List<GroupRotationTeamIdentity>,
        assignments: List<GroupRotationPairingAssignment>,
    ): List<GroupRotationTeamIdentityIssue> {
        val issues = mutableListOf<GroupRotationTeamIdentityIssue>()
        val teamUUIDs = teams.map { it.teamUUID }.toSet()
        assignments.forEach { assignment ->
            if (!isCanonicalUuid(assignment.teamUUID)) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.INVALID_TEAM_UUID,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.tournamentId != tournament.id) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.TOURNAMENT_ID_MISMATCH,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.pairingKey !in tournament.selectedGroupPairings) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.UNSELECTED_PAIRING,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT,
                    teamUUID = assignment.teamUUID,
                    lobbySlotNumber = assignment.lobbySlotNumber,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
            if (assignment.teamUUID !in teamUUIDs) {
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.TEAM_UUID_NOT_FOUND,
                    teamUUID = assignment.teamUUID,
                    pairingKey = assignment.pairingKey.canonicalKey,
                )
            }
        }
        assignments.groupBy { it.pairingKey to it.lobbySlotNumber }.filterValues { it.size > 1 }
            .forEach { (key, _) ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.DUPLICATE_LOBBY_SLOT,
                    lobbySlotNumber = key.second,
                    pairingKey = key.first.canonicalKey,
                )
            }
        assignments.groupBy { it.pairingKey to it.teamUUID }.filterValues { it.size > 1 }
            .forEach { (key, _) ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.DUPLICATE_TEAM_IN_PAIRING,
                    teamUUID = key.second,
                    pairingKey = key.first.canonicalKey,
                )
            }
        return issues
    }

    private fun validatePairing(
        pairingKey: GroupPairing,
        tournament: Tournament,
    ): List<GroupRotationTeamIdentityIssue> = if (pairingKey in tournament.selectedGroupPairings) {
        emptyList()
    } else {
        listOf(
            GroupRotationTeamIdentityIssue(
                GroupRotationTeamIdentityIssueCode.UNSELECTED_PAIRING,
                pairingKey = pairingKey.canonicalKey,
            ),
        )
    }

    private fun validateLobbyShape(
        pairingKey: GroupPairing,
        entries: List<GroupRotationPairingTeamIdentityEntry>,
    ): List<GroupRotationTeamIdentityIssue> {
        val issues = mutableListOf<GroupRotationTeamIdentityIssue>()
        entries.filter { it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS }
            .forEach { entry ->
                issues += GroupRotationTeamIdentityIssue(
                    GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT,
                    lobbySlotNumber = entry.lobbySlotNumber,
                    pairingKey = pairingKey.canonicalKey,
                )
            }
        entries.groupBy { it.lobbySlotNumber }.filterValues { it.size > 1 }.keys.forEach { lobbySlotNumber ->
            issues += GroupRotationTeamIdentityIssue(
                GroupRotationTeamIdentityIssueCode.DUPLICATE_LOBBY_SLOT,
                lobbySlotNumber = lobbySlotNumber,
                pairingKey = pairingKey.canonicalKey,
            )
        }
        return issues
    }

    private fun duplicateEntryNameIssues(
        pairingKey: GroupPairing,
        entries: List<GroupRotationPairingTeamIdentityEntry>,
    ): List<GroupRotationTeamIdentityIssue> = entries
        .groupBy { normalizer.normalize(it.displayName) }
        .filterKeys { it.isNotBlank() }
        .filterValues { it.size > 1 }
        .keys
        .sorted()
        .map { normalizedName ->
            GroupRotationTeamIdentityIssue(
                GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                normalizedName = normalizedName,
                pairingKey = pairingKey.canonicalKey,
            )
        }

    private fun capacityFor(tournament: Tournament): Int = when (tournament.groupCount) {
        3 -> 18
        4 -> 24
        else -> 0
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}
