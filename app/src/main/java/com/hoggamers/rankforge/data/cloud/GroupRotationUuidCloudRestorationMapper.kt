package com.hoggamers.rankforge.data.cloud

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidMatchRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import java.time.LocalDate
import java.util.UUID

sealed interface GroupRotationUuidCloudRestorationMappingResult {
    data class Success(val snapshot: GroupRotationUuidCloudRestorationSnapshot) :
        GroupRotationUuidCloudRestorationMappingResult

    data object NotUuidBacked : GroupRotationUuidCloudRestorationMappingResult
    data object Invalid : GroupRotationUuidCloudRestorationMappingResult
}

/** Validates V4 read rows without deriving identity from numeric TeamSlots. */
object GroupRotationUuidCloudRestorationMapper {
    fun map(
        payloads: GroupRotationUuidCloudRestorationPayloads,
        expectedOwnerUserId: String,
    ): GroupRotationUuidCloudRestorationMappingResult {
        if (expectedOwnerUserId.isBlank()) return invalid("EXPECTED_OWNER_BLANK")
        val parent = when (
            val result = TournamentCloudRestorationMapper.mapSnapshot(payloads.parent)
        ) {
            is TournamentCloudRestorationMappingResult.Success -> result.value.also {
                debugMapper("MAPPER_PARENT_SUCCESS")
            }
            TournamentCloudRestorationMappingResult.Invalid ->
                return invalid("PARENT_INVALID")
        }
        val tournament = parent.tournament
        if (tournament.format != TournamentFormat.GROUP_ROTATION ||
            tournament.ownerUserId != expectedOwnerUserId ||
            parent.cloudRevision == null
        ) {
            return invalid("PARENT_CONTEXT_INVALID")
        }

        val normalizer = GroupRotationTeamIdentityNormalizer()
        val identities = payloads.identities.map { payload ->
            if (payload.tournamentId != tournament.id || !isCanonicalUuid(payload.teamUUID)) {
                return invalid("IDENTITY_ROW_INVALID")
            }
            if (normalizer.cleanDisplayName(payload.displayName).isBlank() ||
                payload.normalizedName != normalizer.normalize(payload.displayName)
            ) {
                return invalid("IDENTITY_NAME_INVALID")
            }
            GroupRotationTeamIdentity(
                tournamentId = payload.tournamentId,
                teamUUID = payload.teamUUID,
                displayName = payload.displayName,
                normalizedName = payload.normalizedName,
            )
        }
        val capacity = when (tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> return invalid("CAPACITY_INVALID")
        }
        if (identities.size > capacity ||
            identities.map { it.teamUUID }.distinct().size != identities.size ||
            identities.map { it.normalizedName }.distinct().size != identities.size
        ) {
            return invalid("IDENTITY_POOL_INVALID")
        }
        val identitiesByUuid = identities.associateBy { it.teamUUID }
        val selectedPairings = tournament.selectedGroupPairings.toSet()

        val assignments = payloads.assignments.map { payload ->
            val pairing = runCatching { GroupPairing.fromCanonicalKey(payload.pairingKey) }
                .getOrNull() ?: return invalid("ASSIGNMENT_PAIRING_INVALID")
            if (payload.tournamentId != tournament.id ||
                pairing !in selectedPairings ||
                payload.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                identitiesByUuid[payload.teamUUID] == null
            ) {
                return invalid("ASSIGNMENT_ROW_INVALID")
            }
            GroupRotationPairingAssignment(
                tournamentId = payload.tournamentId,
                pairingKey = pairing,
                lobbySlotNumber = payload.lobbySlotNumber,
                teamUUID = payload.teamUUID,
            )
        }
        if (assignments.groupBy { it.pairingKey to it.lobbySlotNumber }.any { it.value.size > 1 } ||
            assignments.groupBy { it.pairingKey to it.teamUUID }.any { it.value.size > 1 }
        ) {
            return invalid("ASSIGNMENT_DUPLICATE")
        }

        val baseMatches = payloads.matches
        if (baseMatches.map { it.id }.distinct().size != baseMatches.size ||
            baseMatches.map { it.matchNumber }.distinct().size != baseMatches.size
        ) {
            return invalid("BASE_MATCH_DUPLICATE")
        }
        val baseById = baseMatches.associateBy { it.id }
        if (baseMatches.any { match ->
                val pairingKey = match.groupPairingKey
                !isCanonicalUuid(match.id) ||
                    match.tournamentId != tournament.id ||
                    match.matchNumber !in 1..18 ||
                    runCatching { MatchStatus.valueOf(match.status.uppercase()) }.getOrNull() == null ||
                    pairingKey == null ||
                    runCatching { GroupPairing.fromCanonicalKey(pairingKey) }
                        .getOrNull() !in selectedPairings
            }
        ) {
            return invalid("BASE_MATCH_ROW_INVALID")
        }

        val headers = payloads.uuidMatchSnapshots
        if (headers.map { it.matchId }.distinct().size != headers.size ||
            headers.any { header ->
                !isCanonicalUuid(header.matchId) ||
                    header.tournamentId != tournament.id ||
                    header.matchId !in baseById ||
                    header.revision < 0 ||
                    header.status !in setOf("draft", "finalized")
            }
        ) {
            return invalid("UUID_HEADER_ROW_INVALID")
        }
        if (headers.any { header ->
                val base = baseById.getValue(header.matchId)
                base.status.lowercase() != header.status ||
                    base.groupPairingKey != header.pairingKey ||
                    (header.status == "finalized" &&
                        (header.finalizedAt.isNullOrBlank() ||
                            header.finalizedBy?.let(::isCanonicalUuid) != true)) ||
                    (header.status == "draft" &&
                        (header.finalizedAt != null || header.finalizedBy != null))
            }
        ) {
            return invalid("UUID_HEADER_CONFLICT")
        }

        val assignmentsByMatch = payloads.uuidMatchAssignments.groupBy { it.matchId }
        if (payloads.uuidMatchAssignments.any { row ->
                !isCanonicalUuid(row.matchId) ||
                    row.tournamentId != tournament.id ||
                    row.matchId !in headers.map { it.matchId }.toSet() ||
                    row.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS ||
                    identitiesByUuid[row.teamUUID] == null
            }
        ) {
            return invalid("UUID_ASSIGNMENT_ROW_INVALID")
        }
        if (assignmentsByMatch.values.any { rows ->
                rows.isEmpty() ||
                    rows.groupBy { it.lobbySlotNumber }.any { it.value.size > 1 } ||
                    rows.groupBy { it.teamUUID }.any { it.value.size > 1 }
            }
        ) {
            return invalid("UUID_ASSIGNMENT_SET_INVALID")
        }

        val resultsByMatch = payloads.uuidMatchResults.groupBy { it.matchId }
        if (payloads.uuidMatchResults.any { row ->
                row.tournamentId != tournament.id ||
                    row.matchId !in headers.map { it.matchId }.toSet() ||
                    !isCanonicalUuid(row.teamUUID) ||
                    row.kills < 0 ||
                    (row.placement != null && row.placement !in 1..12) ||
                    row.participationStatus !in setOf("PARTICIPATED", "NO_SHOW")
            }
        ) {
            return invalid("UUID_RESULT_ROW_INVALID")
        }

        val uuidSnapshots = headers.map { header ->
            val base = baseById.getValue(header.matchId)
            val pairing = runCatching { GroupPairing.fromCanonicalKey(header.pairingKey) }
                .getOrNull() ?: return invalid("UUID_HEADER_PAIRING_INVALID")
            val matchAssignments = assignmentsByMatch[header.matchId].orEmpty()
            val assignedUuids = matchAssignments.map { it.teamUUID }.toSet()
            val resultRows = resultsByMatch[header.matchId].orEmpty()
            if (resultRows.groupBy { it.teamUUID }.any { it.value.size > 1 } ||
                resultRows.groupBy { it.placement }.filterKeys { it != null }.any { it.value.size > 1 } ||
                resultRows.any { it.teamUUID !in assignedUuids }
            ) {
                return invalid("UUID_RESULT_SET_INVALID")
            }
            if (resultRows.any { row ->
                    row.participationStatus == "NO_SHOW" &&
                        (row.placement != null || row.kills != 0)
                }
            ) {
                return invalid("UUID_NO_SHOW_INVALID")
            }
            val participated = resultRows.filter { it.participationStatus == "PARTICIPATED" }
            if (header.status == "finalized") {
                if (resultRows.size != assignedUuids.size || participated.isEmpty() ||
                    participated.mapNotNull { it.placement }.toSet() != (1..participated.size).toSet()
                ) {
                    return invalid("UUID_FINALIZED_RESULT_INVALID")
                }
            }
            val identitySnapshot = GroupRotationMatchIdentitySnapshot(
                tournamentId = tournament.id,
                pairing = pairing,
                teams = matchAssignments.sortedBy { it.lobbySlotNumber }.map { assignment ->
                    val identity = identitiesByUuid.getValue(assignment.teamUUID)
                    GroupRotationMatchTeamIdentity(
                        lobbySlotNumber = assignment.lobbySlotNumber,
                        teamUUID = identity.teamUUID,
                        displayName = identity.displayName,
                    )
                },
            )
            val participantResults = resultRows.sortedBy { it.teamUUID }.map { row ->
                GroupRotationMatchParticipantResult(
                    teamUUID = row.teamUUID,
                    participationStatus = MatchParticipationStatus.valueOf(row.participationStatus),
                    placement = row.placement,
                    kills = row.kills,
                    pointAdjustment = row.pointAdjustment,
                )
            }
            GroupRotationUuidMatchRestorationSnapshot(
                match = Match(
                    id = base.id,
                    tournamentId = base.tournamentId,
                    matchNumber = base.matchNumber,
                    date = runCatching { LocalDate.parse(base.matchDate) }.getOrNull()
                        ?: return invalid("BASE_MATCH_DATE_INVALID"),
                    mapName = base.mapName,
                    status = MatchStatus.valueOf(header.status.uppercase()),
                    groupPairing = pairing,
                ),
                identity = identitySnapshot,
                participantResults = participantResults,
            )
        }

        val uuidMatchIds = uuidSnapshots.map { it.match.id }.toSet()
        val legacyMatches = baseMatches.filter { it.id !in uuidMatchIds }
        val legacyMappingSlotsByPairing = payloads.parent.pairingLobbySlots
            .groupBy { it.pairingKey }
            .mapValues { (_, mappings) -> mappings.map { it.teamSlotNumber }.toSet() }
        val hasUuidRegistryData = identities.isNotEmpty() || assignments.isNotEmpty()
        val tournamentUuid = runCatching { UUID.fromString(tournament.id) }.getOrNull()
        val legacyEvidenceMatchIds = legacyMatches
            .filter { match ->
                val mappingSlots = legacyMappingSlotsByPairing[match.groupPairingKey].orEmpty()
                if (mappingSlots.isEmpty()) return@filter false
                if (!hasUuidRegistryData) return@filter true

                val legacyRows = payloads.legacyResults.filter { it.matchId == match.id }
                legacyRows.isNotEmpty() && tournamentUuid != null && legacyRows.all { row ->
                    mappingSlots.any { slotNumber ->
                        TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber) == row.teamSlotId
                    }
                }
            }
            .map { it.id }
            .toSet()
        if (legacyMatches.any { it.id !in legacyEvidenceMatchIds }) {
            return invalid("MATCH_IDENTITY_AMBIGUOUS")
        }
        val legacySnapshot = if (legacyMatches.isEmpty()) {
            emptyList()
        } else {
            when (
                val result = MatchCloudRestorationMapper.map(
                    MatchCloudRestorationPayloads(
                        tournamentId = tournament.id,
                        matches = legacyMatches,
                        results = payloads.legacyResults.filter { row ->
                            row.matchId in legacyMatches.map { it.id }.toSet()
                        },
                        cloudRevision = parent.cloudRevision.value,
                    ),
                )
            ) {
                is MatchCloudRestorationMappingResult.Success -> result.value.matches
                MatchCloudRestorationMappingResult.Invalid ->
                    return invalid("LEGACY_MATCH_MAPPING_INVALID")
            }
        }

        val allMatches = (legacySnapshot + uuidSnapshots.map { it.match })
            .sortedWith(compareBy({ it.matchNumber }, { it.id }))
        if (allMatches.map { it.id }.distinct().size != allMatches.size) {
            return invalid("RESTORED_MATCH_DUPLICATE")
        }
        if (uuidSnapshots.isEmpty() && identities.isEmpty() && assignments.isEmpty()) {
            return GroupRotationUuidCloudRestorationMappingResult.NotUuidBacked
        }
        return GroupRotationUuidCloudRestorationMappingResult.Success(
            GroupRotationUuidCloudRestorationSnapshot(
                parent = parent,
                identities = identities,
                assignments = assignments.sortedWith(compareBy({ it.pairingKey.canonicalKey }, { it.lobbySlotNumber })),
                matches = allMatches,
                uuidMatches = uuidSnapshots,
                cloudRevision = parent.cloudRevision,
            ),
        )
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}

private const val RESTORE_LOG_TAG = "POINTIQ_V4_RESTORE"

private fun invalid(code: String): GroupRotationUuidCloudRestorationMappingResult {
    debugMapper("MAPPER_INVALID_$code")
    return GroupRotationUuidCloudRestorationMappingResult.Invalid
}

private fun debugMapper(code: String) {
    if (BuildConfig.DEBUG) Log.d(RESTORE_LOG_TAG, code)
}
