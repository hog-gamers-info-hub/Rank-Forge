package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationMatchIdentitySnapshotTest {
    private val resolver = GroupRotationMatchIdentitySnapshotResolver()

    @Test
    fun savedUuidIsPreservedExactly() {
        val uuid = "team-uuid-alpha"
        val result = resolve(
            teams = listOf(identity(uuid, "Alpha")),
            assignments = listOf(assignment("A:B", 4, uuid)),
        )

        val snapshot = loaded(result)
        assertEquals(uuid, snapshot.teams.single().teamUUID)
        assertEquals(4, snapshot.teams.single().lobbySlotNumber)
    }

    @Test
    fun sameUuidInDifferentPairingsResolvesIndependently() {
        val uuid = "team-uuid-shared"
        val result = resolve(
            pairingKey = "A:C",
            teams = listOf(identity(uuid, "Shared")),
            assignments = listOf(
                assignment("A:B", 2, uuid),
                assignment("A:C", 9, uuid),
            ),
        )

        val snapshot = loaded(result)
        assertEquals(listOf(9), snapshot.teams.map { it.lobbySlotNumber })
        assertEquals(uuid, snapshot.teams.single().teamUUID)
    }

    @Test
    fun nonConsecutiveOccupiedLobbySlotsAreSortedAndPreserved() {
        val result = resolve(
            teams = listOf(identity("team-2", "Two"), identity("team-10", "Ten")),
            assignments = listOf(
                assignment("A:B", 10, "team-10"),
                assignment("A:B", 2, "team-2"),
            ),
        )

        val snapshot = loaded(result)
        assertEquals(listOf(2, 10), snapshot.teams.map { it.lobbySlotNumber })
    }

    @Test
    fun emptyPositionsDoNotCreatePlaceholderIdentities() {
        val result = resolve(
            teams = listOf(identity("team-2", "Two")),
            assignments = listOf(assignment("A:B", 2, "team-2")),
        )

        assertEquals(1, loaded(result).teams.size)
    }

    @Test
    fun emptyPairingIsValidSnapshot() {
        val result = resolve(
            pairingKey = "A:C",
            teams = listOf(identity("team-2", "Two")),
            assignments = listOf(assignment("A:B", 2, "team-2")),
        )

        assertTrue(result is GroupRotationMatchIdentitySnapshotResult.Loaded)
        assertTrue(loaded(result).teams.isEmpty())
    }

    @Test
    fun clearedAssignmentRetainsUuidButIsNotEligibleForANewMatch() {
        val uuid = "team-uuid-cleared"
        val result = resolve(
            teams = listOf(identity(uuid, "Alpha")),
            assignments = listOf(assignment("A:B", 1, uuid, nameCleared = true)),
        )

        assertTrue(result is GroupRotationMatchIdentitySnapshotResult.Loaded)
        assertTrue(loaded(result).teams.isEmpty())
    }

    @Test
    fun existingDraftModeRetainsClearedAssignmentUuid() {
        val uuid = "team-uuid-cleared"
        val result = resolver.resolve(
            tournament = tournament(),
            pairing = pairing("A:B"),
            saved = GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(identity(uuid, "Aarav")),
                assignments = listOf(assignment("A:B", 7, uuid, nameCleared = true)),
            ),
            includeClearedAssignments = true,
        )

        val team = loaded(result).teams.single()
        assertEquals(7, team.lobbySlotNumber)
        assertEquals(uuid, team.teamUUID)
        assertEquals("Aarav", team.displayName)
        assertTrue(team.nameCleared)
    }

    @Test
    fun clearedNameIsPairingSpecificWhenUuidIsShared() {
        val uuid = "team-uuid-shared"
        val saved = GroupRotationTeamIdentityLocalSnapshot(
            teams = listOf(identity(uuid, "Aarav")),
            assignments = listOf(
                assignment("A:B", 1, uuid, nameCleared = true),
                assignment("A:C", 2, uuid),
            ),
        )

        val first = loaded(
            resolver.resolve(
                tournament = tournament(),
                pairing = pairing("A:B"),
                saved = saved,
                includeClearedAssignments = true,
            ),
        )
        val second = loaded(
            resolver.resolve(
                tournament = tournament(),
                pairing = pairing("A:C"),
                saved = saved,
                includeClearedAssignments = true,
            ),
        )

        assertTrue(first.teams.single().nameCleared)
        assertTrue(second.teams.single().nameCleared.not())
    }

    @Test
    fun missingUuidFails() {
        val result = resolve(
            assignments = listOf(assignment("A:B", 1, "missing")),
        )

        assertInvalidWith(result, GroupRotationMatchIdentitySnapshotIssueCode.TEAM_UUID_NOT_FOUND)
    }

    @Test
    fun duplicateAssignmentFails() {
        val result = resolve(
            teams = listOf(identity("team-1", "One"), identity("team-2", "Two")),
            assignments = listOf(
                assignment("A:B", 1, "team-1"),
                assignment("A:B", 1, "team-2"),
            ),
        )

        assertInvalidWith(result, GroupRotationMatchIdentitySnapshotIssueCode.DUPLICATE_LOBBY_SLOT)
    }

    @Test
    fun tournamentAndPairingMismatchFails() {
        val result = resolver.resolve(
            tournament = tournament(id = TOURNAMENT_ID),
            pairing = pairing("A:B"),
            saved = GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(identity("team-1", "One", tournamentId = "other-tournament")),
                assignments = listOf(
                    assignment("B:C", 1, "team-1", tournamentId = "other-tournament"),
                ),
            ),
        )

        assertInvalidWith(result, GroupRotationMatchIdentitySnapshotIssueCode.TEAM_UUID_TOURNAMENT_MISMATCH)
        assertInvalidWith(result, GroupRotationMatchIdentitySnapshotIssueCode.TOURNAMENT_ID_MISMATCH)
        assertInvalidWith(result, GroupRotationMatchIdentitySnapshotIssueCode.UNSELECTED_PAIRING)
    }

    @Test
    fun renamedRegistryTeamUsesCurrentNameAndPreservesUuid() {
        val uuid = "team-uuid-renamed"
        val result = resolve(
            teams = listOf(identity(uuid, "Phoenix Renamed")),
            assignments = listOf(assignment("A:B", 7, uuid)),
        )

        val team = loaded(result).teams.single()
        assertEquals(uuid, team.teamUUID)
        assertEquals("Phoenix Renamed", team.displayName)
    }

    private fun resolve(
        pairingKey: String = "A:B",
        tournament: Tournament = tournament(),
        teams: List<GroupRotationTeamIdentity> = emptyList(),
        assignments: List<GroupRotationPairingAssignment> = emptyList(),
    ): GroupRotationMatchIdentitySnapshotResult = resolver.resolve(
        tournament = tournament,
        pairing = pairing(pairingKey),
        saved = GroupRotationTeamIdentityLocalSnapshot(teams, assignments),
    )

    private fun loaded(
        result: GroupRotationMatchIdentitySnapshotResult,
    ): GroupRotationMatchIdentitySnapshot = (result as GroupRotationMatchIdentitySnapshotResult.Loaded).snapshot

    private fun assertInvalidWith(
        result: GroupRotationMatchIdentitySnapshotResult,
        code: GroupRotationMatchIdentitySnapshotIssueCode,
    ) {
        assertTrue(result is GroupRotationMatchIdentitySnapshotResult.Invalid)
        assertTrue((result as GroupRotationMatchIdentitySnapshotResult.Invalid).issues.any { it.code == code })
    }

    private fun identity(
        uuid: String,
        name: String,
        tournamentId: String = TOURNAMENT_ID,
    ) = GroupRotationTeamIdentity(
        tournamentId = tournamentId,
        teamUUID = uuid,
        displayName = name,
        normalizedName = name.lowercase(),
    )

    private fun assignment(
        pairingKey: String,
        lobbySlotNumber: Int,
        uuid: String,
        tournamentId: String = TOURNAMENT_ID,
        nameCleared: Boolean = false,
    ) = GroupRotationPairingAssignment(
        tournamentId = tournamentId,
        pairingKey = pairing(pairingKey),
        lobbySlotNumber = lobbySlotNumber,
        teamUUID = uuid,
        nameCleared = nameCleared,
    )

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private fun tournament(id: String = TOURNAMENT_ID) = Tournament(
        id = id,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C")),
    )

    private companion object {
        const val TOURNAMENT_ID = "tournament-match-identity"
    }
}
