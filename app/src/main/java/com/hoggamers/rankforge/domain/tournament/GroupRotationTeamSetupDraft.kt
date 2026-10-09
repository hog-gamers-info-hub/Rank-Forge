package com.hoggamers.rankforge.domain.tournament

import kotlinx.coroutines.flow.Flow

enum class GroupRotationTeamSetupDraftIssueCode {
    NOT_GROUP_ROTATION,
    TOURNAMENT_ID_MISMATCH,
    EMPTY_CANDIDATE,
    MISSING_PAIRING,
    EXTRA_PAIRING,
    INVALID_PAIRING_ENTRY_COUNT,
    MISSING_LOBBY_SLOT,
    DUPLICATE_LOBBY_SLOT,
    INVALID_LOBBY_SLOT,
}

data class GroupRotationTeamSetupDraftIssue(
    val code: GroupRotationTeamSetupDraftIssueCode,
    val pairingKey: String? = null,
    val lobbySlotNumber: Int? = null,
)

sealed interface GroupRotationTeamSetupDraftSaveResult {
    data object Saved : GroupRotationTeamSetupDraftSaveResult
    data class InvalidDraft(
        val issues: List<GroupRotationTeamSetupDraftIssue>,
    ) : GroupRotationTeamSetupDraftSaveResult
}

interface GroupRotationTeamSetupDraftRepository {
    fun observeDraft(tournamentId: String): Flow<List<GroupRotationPairingTeamEntry>>

    suspend fun readDraft(tournamentId: String): List<GroupRotationPairingTeamEntry>

    suspend fun replaceDraft(
        tournament: Tournament,
        candidate: GroupRotationTeamSetupCandidate,
    ): GroupRotationTeamSetupDraftSaveResult

    suspend fun clearDraft(tournamentId: String)

    suspend fun clearDraftForPairing(
        tournamentId: String,
        pairing: GroupPairing,
    )
}

fun validateGroupRotationTeamSetupDraft(
    tournament: Tournament,
    candidate: GroupRotationTeamSetupCandidate,
): List<GroupRotationTeamSetupDraftIssue> {
    val issues = mutableListOf<GroupRotationTeamSetupDraftIssue>()
    if (tournament.format != TournamentFormat.GROUP_ROTATION) {
        issues += GroupRotationTeamSetupDraftIssue(GroupRotationTeamSetupDraftIssueCode.NOT_GROUP_ROTATION)
    }
    if (candidate.tournamentId != tournament.id) {
        issues += GroupRotationTeamSetupDraftIssue(GroupRotationTeamSetupDraftIssueCode.TOURNAMENT_ID_MISMATCH)
    }
    if (candidate.entries.isEmpty()) {
        issues += GroupRotationTeamSetupDraftIssue(GroupRotationTeamSetupDraftIssueCode.EMPTY_CANDIDATE)
    }
    val expectedKeys = tournament.selectedGroupPairings.map { it.canonicalKey }.toSet()
    val entriesByPairing = candidate.entries.groupBy { it.pairing.canonicalKey }
    entriesByPairing.keys.minus(expectedKeys).sorted().forEach { pairingKey ->
        issues += GroupRotationTeamSetupDraftIssue(
            code = GroupRotationTeamSetupDraftIssueCode.EXTRA_PAIRING,
            pairingKey = pairingKey,
        )
    }
    entriesByPairing.keys.intersect(expectedKeys).sorted().forEach { pairingKey ->
        val entries = entriesByPairing.getValue(pairingKey)
        if (entries.size != GroupRotationPairingLobbySlot.MAX_LOBBY_SLOT_NUMBER) {
            issues += GroupRotationTeamSetupDraftIssue(
                code = GroupRotationTeamSetupDraftIssueCode.INVALID_PAIRING_ENTRY_COUNT,
                pairingKey = pairingKey,
            )
        }
        val entriesByLobbySlot = entries.groupBy { it.lobbySlotNumber }
        GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS
            .filter { it !in entriesByLobbySlot }
            .forEach { lobbySlotNumber ->
                issues += GroupRotationTeamSetupDraftIssue(
                    code = GroupRotationTeamSetupDraftIssueCode.MISSING_LOBBY_SLOT,
                    pairingKey = pairingKey,
                    lobbySlotNumber = lobbySlotNumber,
                )
            }
        entriesByLobbySlot
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach { lobbySlotNumber ->
                issues += GroupRotationTeamSetupDraftIssue(
                    code = GroupRotationTeamSetupDraftIssueCode.DUPLICATE_LOBBY_SLOT,
                    pairingKey = pairingKey,
                    lobbySlotNumber = lobbySlotNumber,
                )
            }
        entries.filter { it.lobbySlotNumber !in GroupRotationPairingLobbySlot.LOBBY_SLOT_NUMBERS }
            .forEach { entry ->
                issues += GroupRotationTeamSetupDraftIssue(
                    code = GroupRotationTeamSetupDraftIssueCode.INVALID_LOBBY_SLOT,
                    pairingKey = pairingKey,
                    lobbySlotNumber = entry.lobbySlotNumber,
                )
            }
    }
    return issues.distinct()
}

