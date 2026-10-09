package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationTeamSetupDraftTest {
    @Test
    fun draftValidationAcceptsPartialPairingsAndPreservesBlankRawNames() {
        val tournament = tournament(pairings = listOf("A:B", "B:C"))
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..12).map { lobbySlot ->
                GroupRotationPairingTeamEntry(
                    pairing = GroupPairing.fromCanonicalKey("A:B"),
                    lobbySlotNumber = lobbySlot,
                    teamName = if (lobbySlot == 1) "  raw  name  " else "",
                )
            },
        )

        val issues = validateGroupRotationTeamSetupDraft(tournament, candidate)

        assertEquals(emptyList<GroupRotationTeamSetupDraftIssue>(), issues)
        assertTrue(issues.none { it.code == GroupRotationTeamSetupDraftIssueCode.EXTRA_PAIRING })
        assertEquals("  raw  name  ", candidate.entries.first().teamName)
        assertEquals("", candidate.entries[1].teamName)
    }

    @Test
    fun emptyDraftCandidateIsRejected() {
        val issues = validateGroupRotationTeamSetupDraft(
            tournament(pairings = listOf("A:B", "B:C")),
            GroupRotationTeamSetupCandidate(TOURNAMENT_ID, emptyList()),
        )

        assertTrue(issues.any { it.code == GroupRotationTeamSetupDraftIssueCode.EMPTY_CANDIDATE })
        assertTrue(issues.none { it.code == GroupRotationTeamSetupDraftIssueCode.MISSING_PAIRING })
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
    fun draftValidationRejectsUnselectedPairing() {
        val candidate = completeCandidate("B:C")

        val issues = validateGroupRotationTeamSetupDraft(tournament(), candidate)

        assertTrue(issues.any {
            it.code == GroupRotationTeamSetupDraftIssueCode.EXTRA_PAIRING && it.pairingKey == "B:C"
        })
    }

    @Test
    fun draftValidationRejectsMissingAndDuplicateLobbySlots() {
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..12).map { lobbySlot ->
                GroupRotationPairingTeamEntry(
                    pairing = GroupPairing.fromCanonicalKey("A:B"),
                    lobbySlotNumber = if (lobbySlot == 12) 11 else lobbySlot,
                    teamName = "Team $lobbySlot",
                )
            },
        )

        val issues = validateGroupRotationTeamSetupDraft(tournament(), candidate)

        assertTrue(issues.any {
            it.code == GroupRotationTeamSetupDraftIssueCode.MISSING_LOBBY_SLOT &&
                it.lobbySlotNumber == 12
        })
        assertTrue(issues.any {
            it.code == GroupRotationTeamSetupDraftIssueCode.DUPLICATE_LOBBY_SLOT &&
                it.lobbySlotNumber == 11
        })
    }

    @Test
    fun draftValidationReportsLobbySlotZeroAsInvalidLobbySlot() {
        assertInvalidLobbySlot(0)
    }

    @Test
    fun draftValidationReportsLobbySlotThirteenAsInvalidLobbySlot() {
        assertInvalidLobbySlot(13)
    }

    private fun tournament(
        pairings: List<String> = listOf("A:B"),
    ) = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = pairings.map(GroupPairing::fromCanonicalKey),
    )

    private fun completeCandidate(pairingKey: String) = GroupRotationTeamSetupCandidate(
        TOURNAMENT_ID,
        (1..12).map { lobbySlot ->
            GroupRotationPairingTeamEntry(
                pairing = GroupPairing.fromCanonicalKey(pairingKey),
                lobbySlotNumber = lobbySlot,
                teamName = "Team $lobbySlot",
            )
        },
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

