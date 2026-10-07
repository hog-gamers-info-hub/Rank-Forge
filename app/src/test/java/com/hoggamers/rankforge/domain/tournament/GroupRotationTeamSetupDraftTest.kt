package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationTeamSetupDraftTest {
    @Test
    fun draftValidationPreservesBlankRawNamesAndRequiresCompletePairings() {
        val tournament = tournament()
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..11).map { lobbySlot ->
                GroupRotationPairingTeamEntry(
                    pairing = GroupPairing.fromCanonicalKey("A:B"),
                    lobbySlotNumber = lobbySlot,
                    teamName = if (lobbySlot == 1) "  raw  name  " else "",
                )
            },
        )

        val issues = validateGroupRotationTeamSetupDraft(tournament, candidate)

        assertTrue(issues.any { it.code == GroupRotationTeamSetupDraftIssueCode.INVALID_PAIRING_ENTRY_COUNT })
        assertTrue(issues.any { it.code == GroupRotationTeamSetupDraftIssueCode.MISSING_LOBBY_SLOT })
        assertTrue(issues.none { it.code == GroupRotationTeamSetupDraftIssueCode.EXTRA_PAIRING })
    }

    @Test
    fun completeDraftValidationDoesNotNormalizeRawText() {
        val tournament = tournament()
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..12).map { lobbySlot ->
                GroupRotationPairingTeamEntry(
                    pairing = GroupPairing.fromCanonicalKey("A:B"),
                    lobbySlotNumber = lobbySlot,
                    teamName = if (lobbySlot == 1) "  Raw  Team  " else "",
                )
            },
        )

        assertEquals(emptyList<GroupRotationTeamSetupDraftIssue>(), validateGroupRotationTeamSetupDraft(tournament, candidate))
        assertEquals("  Raw  Team  ", candidate.entries.first().teamName)
    }

    @Test
    fun draftValidationReportsLobbySlotZeroAsInvalidLobbySlot() {
        assertInvalidLobbySlot(0)
    }

    @Test
    fun draftValidationReportsLobbySlotThirteenAsInvalidLobbySlot() {
        assertInvalidLobbySlot(13)
    }

    private fun tournament() = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(GroupPairing.fromCanonicalKey("A:B")),
    )

    private fun assertInvalidLobbySlot(lobbySlotNumber: Int) {
        val tournament = tournament()
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..12).map { slot ->
                GroupRotationPairingTeamEntry(
                    pairing = GroupPairing.fromCanonicalKey("A:B"),
                    lobbySlotNumber = if (slot == 1) lobbySlotNumber else slot,
                    teamName = "Team $slot",
                )
            },
        )

        val issues = validateGroupRotationTeamSetupDraft(tournament, candidate)

        assertTrue(
            issues.any {
                it.code == GroupRotationTeamSetupDraftIssueCode.INVALID_LOBBY_SLOT &&
                    it.lobbySlotNumber == lobbySlotNumber
            },
        )
        assertTrue(
            issues.none {
                it.code == GroupRotationTeamSetupDraftIssueCode.DUPLICATE_LOBBY_SLOT &&
                    it.lobbySlotNumber == lobbySlotNumber
            },
        )
    }

    private companion object {
        const val TOURNAMENT_ID = "draft-test"
    }
}

