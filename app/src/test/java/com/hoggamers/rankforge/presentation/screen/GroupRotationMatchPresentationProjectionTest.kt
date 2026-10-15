package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchDraftState
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.MatchDraftFieldValues
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationMatchPresentationProjectionTest {
    @Test
    fun sparseLobbyRowsKeepSavedUuidAndCurrentName() {
        val result = GroupRotationMatchPresentationProjection.project(
            state = state(
                teams = listOf(
                    team(11, "uuid-titan", "TITANS"),
                    team(3, "uuid-dragon", "DRAGONS"),
                ),
                participants = listOf(
                    GroupRotationMatchParticipantResult(
                        teamUUID = "uuid-dragon",
                        participationStatus = MatchParticipationStatus.PARTICIPATED,
                        placement = 2,
                        kills = 4,
                    ),
                ),
                values = mapOf(3 to MatchDraftFieldValues(placementInput = "1", killsInput = "7")),
            ),
        ) as GroupRotationMatchPresentationProjectionResult.Loaded

        assertEquals(listOf(3, 11), result.rows.map { it.lobbySlotNumber })
        assertEquals(listOf("uuid-dragon", "uuid-titan"), result.rows.map { it.teamUUID })
        assertEquals("DRAGONS", result.rows.first().displayName)
        assertEquals("1", result.rows.first().placementInput)
        assertEquals("7", result.rows.first().killsInput)
    }

    @Test
    fun renamedRegistryDisplayNameDoesNotChangeSavedUuid() {
        val result = GroupRotationMatchPresentationProjection.project(
            state(teams = listOf(team(3, "uuid-dragon", "PHOENIX"))),
        ) as GroupRotationMatchPresentationProjectionResult.Loaded

        assertEquals("uuid-dragon", result.rows.single().teamUUID)
        assertEquals("PHOENIX", result.rows.single().displayName)
    }

    @Test
    fun staleLobbyDraftAndDuplicateUuidFailClosed() {
        val stale = GroupRotationMatchPresentationProjection.project(
            state(
                teams = listOf(team(3, "uuid-dragon", "DRAGONS")),
                values = mapOf(7 to MatchDraftFieldValues(placementInput = "1")),
            ),
        )
        val duplicate = GroupRotationMatchPresentationProjection.project(
            state(
                teams = listOf(
                    team(3, "uuid-dragon", "DRAGONS"),
                    team(7, "uuid-dragon", "DRAGONS"),
                ),
            ),
        )

        assertTrue(stale is GroupRotationMatchPresentationProjectionResult.Invalid)
        assertTrue(duplicate is GroupRotationMatchPresentationProjectionResult.Invalid)
    }

    private fun state(
        teams: List<GroupRotationMatchTeamIdentity>,
        participants: List<GroupRotationMatchParticipantResult> = emptyList(),
        values: Map<Int, MatchDraftFieldValues> = emptyMap(),
    ) = GroupRotationMatchDraftState(
        snapshot = GroupRotationMatchIdentitySnapshot(
            tournamentId = "tournament-1",
            pairing = GroupPairing.fromCanonicalKey("A:B"),
            teams = teams,
        ),
        participantResults = participants,
        valuesByLobbySlot = values,
    )

    private fun team(lobby: Int, uuid: String, name: String) = GroupRotationMatchTeamIdentity(
        lobbySlotNumber = lobby,
        teamUUID = uuid,
        displayName = name,
    )
}
