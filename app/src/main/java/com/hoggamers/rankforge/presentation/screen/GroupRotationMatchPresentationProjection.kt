package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftState
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot

/** Presentation-only rows for UUID-backed Group Rotation match processing. */
internal data class GroupRotationMatchPresentationRow(
    val lobbySlotNumber: Int,
    val teamUUID: String,
    val displayName: String,
    val placementInput: String,
    val killsInput: String,
    val pointAdjustment: Int,
)

internal sealed interface GroupRotationMatchPresentationProjectionResult {
    data class Loaded(
        val snapshot: GroupRotationMatchIdentitySnapshot,
        val rows: List<GroupRotationMatchPresentationRow>,
    ) : GroupRotationMatchPresentationProjectionResult

    data object Invalid : GroupRotationMatchPresentationProjectionResult
}

internal object GroupRotationMatchPresentationProjection {
    fun project(
        state: GroupRotationMatchDraftState,
    ): GroupRotationMatchPresentationProjectionResult {
        val snapshot = state.snapshot
        if (snapshot.teams.any { it.lobbySlotNumber !in 1..12 || it.teamUUID.isBlank() }) {
            return GroupRotationMatchPresentationProjectionResult.Invalid
        }
        if (snapshot.teams.groupBy { it.lobbySlotNumber }.any { it.value.size > 1 } ||
            snapshot.teams.groupBy { it.teamUUID }.any { it.value.size > 1 }
        ) {
            return GroupRotationMatchPresentationProjectionResult.Invalid
        }
        val identities = snapshot.teams.associateBy { it.teamUUID }
        if (state.participantResults.any { it.teamUUID !in identities } ||
            state.participantResults.groupBy { it.teamUUID }.any { it.value.size > 1 } ||
            state.valuesByLobbySlot.keys.any { lobby -> snapshot.teams.none { it.lobbySlotNumber == lobby } }
        ) {
            return GroupRotationMatchPresentationProjectionResult.Invalid
        }
        val participantsByUuid = state.participantResults.associateBy { it.teamUUID }
        val rows = snapshot.teams.sortedBy { it.lobbySlotNumber }.map { team ->
            val participant = participantsByUuid[team.teamUUID]
            val draft = state.valuesByLobbySlot[team.lobbySlotNumber]
            GroupRotationMatchPresentationRow(
                lobbySlotNumber = team.lobbySlotNumber,
                teamUUID = team.teamUUID,
                displayName = team.displayName,
                placementInput = draft?.placementInput ?: participant?.placement?.toString().orEmpty(),
                killsInput = draft?.killsInput ?: participant?.kills?.toString().orEmpty(),
                pointAdjustment = draft?.pointAdjustment ?: participant?.pointAdjustment ?: 0,
            )
        }
        return GroupRotationMatchPresentationProjectionResult.Loaded(snapshot, rows)
    }
}
