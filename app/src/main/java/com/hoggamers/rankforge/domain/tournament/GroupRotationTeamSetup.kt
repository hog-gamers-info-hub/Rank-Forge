package com.hoggamers.rankforge.domain.tournament

import java.util.Locale

/** Identity rules used only for Group Rotation pairing-team setup. */
class GroupRotationTeamIdentityNormalizer {
    fun cleanDisplayName(value: String): String = value.trim().replace(WHITESPACE, " ")

    fun normalize(value: String): String = cleanDisplayName(value).lowercase(Locale.ROOT)

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

data class GroupRotationPairingTeamEntry(
    val pairing: GroupPairing,
    val lobbySlotNumber: Int,
    val teamName: String,
)

data class GroupRotationTeamSetupCandidate(
    val tournamentId: String,
    val entries: List<GroupRotationPairingTeamEntry>,
)

data class GroupRotationTeamSetupValidatedEntry(
    val pairing: GroupPairing,
    val lobbySlotNumber: Int,
    val displayTeamName: String,
    val normalizedTeamIdentity: String,
)

enum class GroupRotationTeamSetupIssueCode {
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    MISSING_PAIRING,
    EXTRA_PAIRING,
    DUPLICATE_PAIRING,
    INVALID_PAIRING_ENTRY_COUNT,
    MISSING_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    INVALID_LOBBY_SLOT,
    BLANK_TEAM_NAME,
    DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING,
    TOO_MANY_UNIQUE_TEAMS,
    EXISTING_SLOT_OUT_OF_RANGE,
    AMBIGUOUS_EXISTING_IDENTITY,
    NO_AVAILABLE_CANONICAL_SLOT,
}

data class GroupRotationTeamSetupIssue(
    val code: GroupRotationTeamSetupIssueCode,
    val pairingKey: String? = null,
    val lobbySlotNumber: Int? = null,
    val normalizedIdentity: String? = null,
    val slotNumbers: List<Int> = emptyList(),
)

sealed interface GroupRotationTeamSetupValidationResult {
    data class Valid(
        val entries: List<GroupRotationTeamSetupValidatedEntry>,
        val normalizedIdentities: Set<String>,
    ) : GroupRotationTeamSetupValidationResult

    data class Invalid(
        val issues: List<GroupRotationTeamSetupIssue>,
    ) : GroupRotationTeamSetupValidationResult
}

class GroupRotationTeamSetupValidator(
    private val normalizer: GroupRotationTeamIdentityNormalizer = GroupRotationTeamIdentityNormalizer(),
) {
    fun validate(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
    ): GroupRotationTeamSetupValidationResult {
        val issues = mutableListOf<GroupRotationTeamSetupIssue>()
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            issues += GroupRotationTeamSetupIssue(GroupRotationTeamSetupIssueCode.NOT_GROUP_ROTATION)
        }
        if (candidate.tournamentId != tournament.id) {
            issues += GroupRotationTeamSetupIssue(GroupRotationTeamSetupIssueCode.TOURNAMENT_ID_MISMATCH)
        }

        val expectedPairings = tournament.selectedGroupPairings.sortedBy { it.canonicalKey }
        val expectedKeys = expectedPairings.map { it.canonicalKey }.toSet()
        val entriesByPairing = candidate.entries.groupBy { it.pairing.canonicalKey }
        val actualKeys = entriesByPairing.keys
        expectedKeys.minus(actualKeys).sorted().forEach { pairingKey ->
            issues += GroupRotationTeamSetupIssue(
                code = GroupRotationTeamSetupIssueCode.MISSING_PAIRING,
                pairingKey = pairingKey,
            )
        }
        actualKeys.minus(expectedKeys).sorted().forEach { pairingKey ->
            issues += GroupRotationTeamSetupIssue(
                code = GroupRotationTeamSetupIssueCode.EXTRA_PAIRING,
                pairingKey = pairingKey,
            )
        }
        candidate.entries
            .groupBy { it.pairing.canonicalKey }
            .filterValues { entries -> entries.map { it.pairing }.distinct().size > 1 }
            .keys
            .sorted()
            .forEach { pairingKey ->
                issues += GroupRotationTeamSetupIssue(
                    code = GroupRotationTeamSetupIssueCode.DUPLICATE_PAIRING,
                    pairingKey = pairingKey,
                )
            }

        val validatedEntries = mutableListOf<GroupRotationTeamSetupValidatedEntry>()
        expectedPairings.forEach { pairing ->
            val pairingKey = pairing.canonicalKey
            val entries = entriesByPairing[pairingKey].orEmpty()
            if (entries.size != GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER) {
                issues += GroupRotationTeamSetupIssue(
                    code = GroupRotationTeamSetupIssueCode.INVALID_PAIRING_ENTRY_COUNT,
                    pairingKey = pairingKey,
                )
            }
            val entriesByLobbySlot = entries.groupBy { it.lobbySlotNumber }
            GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS
                .filter { it !in entriesByLobbySlot }
                .forEach { lobbySlotNumber ->
                    issues += GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.MISSING_LOBBY_SLOT,
                        pairingKey = pairingKey,
                        lobbySlotNumber = lobbySlotNumber,
                    )
                }
            entriesByLobbySlot
                .filterValues { it.size > 1 }
                .keys
                .sorted()
                .forEach { lobbySlotNumber ->
                    issues += GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.DUPLICATE_LOBBY_SLOT,
                        pairingKey = pairingKey,
                        lobbySlotNumber = lobbySlotNumber,
                    )
                }
            entries.filter { it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS }
                .forEach { entry ->
                    issues += GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.INVALID_LOBBY_SLOT,
                        pairingKey = pairingKey,
                        lobbySlotNumber = entry.lobbySlotNumber,
                    )
                }

            val normalizedByIdentity = entries.groupBy { normalizer.normalize(it.teamName) }
            normalizedByIdentity
                .filterKeys { it.isNotBlank() }
                .filterValues { it.size > 1 }
                .keys
                .sorted()
                .forEach { normalizedIdentity ->
                    issues += GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING,
                        pairingKey = pairingKey,
                        normalizedIdentity = normalizedIdentity,
                    )
                }
            entries.forEach { entry ->
                val displayName = normalizer.cleanDisplayName(entry.teamName)
                if (displayName.isBlank()) {
                    issues += GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.BLANK_TEAM_NAME,
                        pairingKey = pairingKey,
                        lobbySlotNumber = entry.lobbySlotNumber,
                    )
                }
                if (entry.lobbySlotNumber in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS && displayName.isNotBlank()) {
                    validatedEntries += GroupRotationTeamSetupValidatedEntry(
                        pairing = pairing,
                        lobbySlotNumber = entry.lobbySlotNumber,
                        displayTeamName = displayName,
                        normalizedTeamIdentity = normalizer.normalize(entry.teamName),
                    )
                }
            }
        }

        val normalizedIdentities = validatedEntries.map { it.normalizedTeamIdentity }.toSet()
        val capacity = when (tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> 0
        }
        if (capacity == 0 || normalizedIdentities.size > capacity) {
            issues += GroupRotationTeamSetupIssue(GroupRotationTeamSetupIssueCode.TOO_MANY_UNIQUE_TEAMS)
        }
        if (issues.isNotEmpty()) return GroupRotationTeamSetupValidationResult.Invalid(issues.distinct())

        return GroupRotationTeamSetupValidationResult.Valid(
            entries = validatedEntries.sortedWith(
                compareBy<GroupRotationTeamSetupValidatedEntry> { it.pairing.canonicalKey }
                    .thenBy { it.lobbySlotNumber },
            ),
            normalizedIdentities = normalizedIdentities,
        )
    }
}

data class GroupRotationTeamSetupPlan(
    val teamSlots: List<TeamSlot>,
    val lobbySlotMappings: List<GroupRotationPairingLobbySlot>,
)

sealed interface GroupRotationTeamSetupPlanningResult {
    data class Planned(val plan: GroupRotationTeamSetupPlan) : GroupRotationTeamSetupPlanningResult

    data class Invalid(
        val issues: List<GroupRotationTeamSetupIssue>,
    ) : GroupRotationTeamSetupPlanningResult
}

class GroupRotationTeamSetupPlanner(
    private val normalizer: GroupRotationTeamIdentityNormalizer = GroupRotationTeamIdentityNormalizer(),
) {
    fun plan(
        tournament: Tournament,
        existingTeamSlots: Collection<TeamSlot>,
        validated: GroupRotationTeamSetupValidationResult.Valid,
    ): GroupRotationTeamSetupPlanningResult {
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return GroupRotationTeamSetupPlanningResult.Invalid(
                listOf(GroupRotationTeamSetupIssue(GroupRotationTeamSetupIssueCode.NOT_GROUP_ROTATION)),
            )
        }
        val expectedSlots = tournament.formatDerivedSlots().sortedBy { it.slotNumber }
        val expectedSlotNumbers = expectedSlots.map { it.slotNumber }.toSet()
        val existingBySlot = existingTeamSlots.groupBy { it.slotNumber }
        val invalidExistingSlots = existingBySlot.keys - expectedSlotNumbers
        if (invalidExistingSlots.isNotEmpty()) {
            return GroupRotationTeamSetupPlanningResult.Invalid(
                invalidExistingSlots.sorted().map { slotNumber ->
                    GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.EXISTING_SLOT_OUT_OF_RANGE,
                        slotNumbers = listOf(slotNumber),
                    )
                },
            )
        }
        val duplicateExistingSlot = existingBySlot.filterValues { it.size > 1 }.keys
        if (duplicateExistingSlot.isNotEmpty()) {
            return GroupRotationTeamSetupPlanningResult.Invalid(
                duplicateExistingSlot.sorted().map { slotNumber ->
                    GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.EXISTING_SLOT_OUT_OF_RANGE,
                        slotNumbers = listOf(slotNumber),
                    )
                },
            )
        }

        val existingByIdentity = existingBySlot.values
            .map { it.single() }
            .filter { it.teamName.isNotBlank() }
            .groupBy { normalizer.normalize(it.teamName) }
        val ambiguousIdentity = existingByIdentity
            .filterValues { slots -> slots.size > 1 }
            .entries
            .sortedBy { it.key }
            .firstOrNull()
        if (ambiguousIdentity != null) {
            return GroupRotationTeamSetupPlanningResult.Invalid(
                listOf(
                    GroupRotationTeamSetupIssue(
                        code = GroupRotationTeamSetupIssueCode.AMBIGUOUS_EXISTING_IDENTITY,
                        normalizedIdentity = ambiguousIdentity.key,
                        slotNumbers = ambiguousIdentity.value.map { it.slotNumber }.sorted(),
                    ),
                ),
            )
        }

        val assignedSlotByIdentity = linkedMapOf<String, Int>()
        val displayNameByIdentity = linkedMapOf<String, String>()
        val existingSlotByIdentity = existingByIdentity.mapValues { (_, slots) -> slots.single().slotNumber }
        val usedSlotNumbers = validated.normalizedIdentities
            .mapNotNull(existingSlotByIdentity::get)
            .toMutableSet()
        val availableSlotNumbers = expectedSlotNumbers.sorted().toMutableList()
        validated.entries.forEach { entry ->
            displayNameByIdentity.putIfAbsent(entry.normalizedTeamIdentity, entry.displayTeamName)
            if (entry.normalizedTeamIdentity in assignedSlotByIdentity) return@forEach
            val reusedSlot = existingSlotByIdentity[entry.normalizedTeamIdentity]
            val slotNumber = reusedSlot ?: availableSlotNumbers.firstOrNull { it !in usedSlotNumbers }
            if (slotNumber == null) {
                return GroupRotationTeamSetupPlanningResult.Invalid(
                    listOf(
                        GroupRotationTeamSetupIssue(
                            code = GroupRotationTeamSetupIssueCode.NO_AVAILABLE_CANONICAL_SLOT,
                            normalizedIdentity = entry.normalizedTeamIdentity,
                        ),
                    ),
                )
            }
            assignedSlotByIdentity[entry.normalizedTeamIdentity] = slotNumber
            usedSlotNumbers += slotNumber
        }

        val identityBySlot = assignedSlotByIdentity.entries.associate { (identity, slot) -> slot to identity }
        val teamSlots = expectedSlots.map { expectedSlot ->
            val existing = existingBySlot[expectedSlot.slotNumber]?.single()
            val baseSlot = existing ?: expectedSlot
            baseSlot.copy(
                teamName = identityBySlot[expectedSlot.slotNumber]
                    ?.let(displayNameByIdentity::get)
                    .orEmpty(),
            )
        }
        val mappings = validated.entries.map { entry ->
            GroupRotationPairingLobbySlot(
                tournamentId = tournament.id,
                pairing = entry.pairing,
                lobbySlotNumber = entry.lobbySlotNumber,
                teamSlotNumber = assignedSlotByIdentity.getValue(entry.normalizedTeamIdentity),
            )
        }
        return GroupRotationTeamSetupPlanningResult.Planned(
            GroupRotationTeamSetupPlan(
                teamSlots = teamSlots,
                lobbySlotMappings = mappings,
            ),
        )
    }
}

interface GroupRotationTeamSetupLocalRepository {
    suspend fun saveGroupRotationTeamSetup(
        candidate: GroupRotationTeamSetupCandidate,
        ownerUserId: String,
    ): GroupRotationTeamSetupLocalSaveResult
}

sealed interface GroupRotationTeamSetupLocalSaveResult {
    data object Saved : GroupRotationTeamSetupLocalSaveResult
    data object TournamentNotFound : GroupRotationTeamSetupLocalSaveResult
    data object ProtectedHistory : GroupRotationTeamSetupLocalSaveResult
    data class InvalidSetup(
        val issues: List<GroupRotationTeamSetupIssue>,
    ) : GroupRotationTeamSetupLocalSaveResult
}

sealed interface SaveGroupRotationTeamSetupResult {
    data object Saved : SaveGroupRotationTeamSetupResult
    data object AuthenticationRequired : SaveGroupRotationTeamSetupResult
    data object TournamentNotFound : SaveGroupRotationTeamSetupResult
    data object ProtectedHistory : SaveGroupRotationTeamSetupResult
    data class InvalidSetup(
        val issues: List<GroupRotationTeamSetupIssue>,
    ) : SaveGroupRotationTeamSetupResult
}

