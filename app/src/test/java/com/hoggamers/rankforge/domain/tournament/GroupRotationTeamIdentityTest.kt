package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationTeamIdentityTest {
    @Test
    fun sameNormalizedNameReusesExistingUuid() {
        val existing = identity(1, "Phoenix")
        val result = resolver(uuid(99)).registerUnassignedTeam(
            tournament = tournament(3, listOf("A:B")),
            displayName = "  PHOENIX  ",
            existingTeams = listOf(existing),
        ) as GroupRotationTeamRegistrationResult.Registered

        assertEquals(existing, result.identity)
        assertEquals(listOf(existing), result.teams)
    }

    @Test
    fun differentNamesCreateDistinctIndependentUuids() {
        val resolver = resolver(uuid(1), uuid(2))
        val tournament = tournament(3, listOf("A:B"))
        val first = resolver.registerUnassignedTeam(tournament, "Alpha", emptyList())
            as GroupRotationTeamRegistrationResult.Registered
        val second = resolver.registerUnassignedTeam(tournament, "Bravo", first.teams)
            as GroupRotationTeamRegistrationResult.Registered

        assertNotEquals(first.identity.teamUUID, second.identity.teamUUID)
        assertEquals(listOf(uuid(1), uuid(2)), second.teams.map { it.teamUUID })
    }

    @Test
    fun differentLobbyPositionsDoNotChangeIdentity() {
        val resolver = resolver(*Array(12) { index -> uuid(index + 1) })
        val tournament = tournament(3, listOf("A:B"))
        val first = resolver.planPairing(
            tournament,
            pairing("A:B"),
            entries("A:B", mapOf(1 to "Alpha")),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned
        val alpha = first.plan.assignments.first { it.lobbySlotNumber == 1 }.teamUUID

        val moved = resolver.planPairing(
            tournament,
            pairing("A:B"),
            entries(
                "A:B",
                buildMap {
                    putAll((1..6).associateWith { "Team ${it + 1}" })
                    put(7, "Alpha")
                    putAll((8..12).associateWith { "Team $it" })
                },
                first.plan.teams,
            ),
            first.plan.teams,
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(alpha, moved.plan.assignments.first { it.lobbySlotNumber == 7 }.teamUUID)
    }

    @Test
    fun pairingAssignmentsRemainIndependentAndMayShareUuidAtDifferentLobbyPositions() {
        val resolver = resolver(*Array(24) { index -> uuid(index + 1) })
        val tournament = tournament(3, listOf("A:B", "A:C"))
        val first = resolver.planPairing(
            tournament,
            pairing("A:B"),
            entries("A:B", mapOf(2 to "Shared")),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned
        val sharedUuid = first.plan.assignments.first { it.lobbySlotNumber == 2 }.teamUUID
        val second = resolver.planPairing(
            tournament,
            pairing("A:C"),
            entries("A:C", mapOf(9 to "Shared"), first.plan.teams, first.plan.assignments),
            first.plan.teams,
            first.plan.assignments,
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(
            sharedUuid,
            second.plan.assignments.first {
                it.pairingKey == pairing("A:C") && it.lobbySlotNumber == 9
            }.teamUUID,
        )
        assertEquals(24, second.plan.assignments.size)
        assertEquals(
            sharedUuid,
            second.plan.assignments.first { it.pairingKey == pairing("A:B") && it.lobbySlotNumber == 2 }.teamUUID,
        )
    }

    @Test
    fun renamePreservesUuidAndAllAssignments() {
        val resolver = resolver(*Array(12) { index -> uuid(index + 1) })
        val tournament = tournament(3, listOf("A:B", "A:C"))
        val first = resolver.planPairing(
            tournament,
            pairing("A:B"),
            entries("A:B", mapOf(1 to "Phoenix")),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned
        val uuid = first.plan.assignments.first { it.lobbySlotNumber == 1 }.teamUUID
        val second = resolver.planPairing(
            tournament,
            pairing("A:C"),
            entries(
                "A:C",
                mapOf(1 to "Team 2", 2 to "Team 3", 3 to "Phoenix"),
                first.plan.teams,
                first.plan.assignments,
            ),
            first.plan.teams,
            first.plan.assignments,
        ) as GroupRotationTeamIdentityPlanResult.Planned

        val renamed = resolver.renameTeam(
            tournament,
            uuid,
            "Phoenix Renamed",
            second.plan.teams,
            second.plan.assignments,
        ) as GroupRotationTeamRenameResult.Renamed

        assertEquals(uuid, renamed.identity.teamUUID)
        assertEquals("phoenix renamed", renamed.identity.normalizedName)
        assertEquals(second.plan.assignments, renamed.assignments)
        assertEquals(2, renamed.assignments.count { it.teamUUID == uuid })
    }

    @Test
    fun capitalizationOnlyRenameWithOmittedUuidUpdatesDisplayNameAndPreservesAssignment() {
        val savedTeam = identity(1, "DRAGONS")
        val savedAssignment = assignment("A:B", 1, savedTeam.teamUUID)
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(1, "Dragons")),
            listOf(savedTeam),
            listOf(savedAssignment),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(savedTeam.teamUUID, result.plan.teams.single().teamUUID)
        assertEquals("Dragons", result.plan.teams.single().displayName)
        assertEquals("dragons", result.plan.teams.single().normalizedName)
        assertEquals(listOf(savedAssignment), result.plan.assignments)
    }

    @Test
    fun capitalizationOnlyRenameUpdatesDisplayNameForPhoenix() {
        val savedTeam = identity(1, "Phoenix")
        val savedAssignment = assignment("A:B", 1, savedTeam.teamUUID)
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(1, "PHOENIX", savedTeam.teamUUID)),
            listOf(savedTeam),
            listOf(savedAssignment),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(savedTeam.teamUUID, result.plan.teams.single().teamUUID)
        assertEquals("PHOENIX", result.plan.teams.single().displayName)
        assertEquals(listOf(savedAssignment), result.plan.assignments)
    }

    @Test
    fun conflictingSubmittedUuidForAssignedLobbyIsRejected() {
        val savedTeam = identity(1, "Alpha")
        val conflictingTeam = identity(2, "Bravo")
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(1, "Alpha", conflictingTeam.teamUUID)),
            listOf(savedTeam, conflictingTeam),
            listOf(assignment("A:B", 1, savedTeam.teamUUID)),
        )

        assertIssue(result, GroupRotationTeamIdentityIssueCode.ASSIGNED_TEAM_UUID_CONFLICT)
    }

    @Test
    fun renameCollisionIsRejected() {
        val resolver = resolver(uuid(1), uuid(2))
        val tournament = tournament(3, listOf("A:B"))
        val result = resolver.renameTeam(
            tournament,
            uuid(1),
            " bravo ",
            listOf(identity(1, "Alpha"), identity(2, "Bravo")),
            emptyList(),
        )

        assertTrue(result is GroupRotationTeamRenameResult.Invalid)
        assertEquals(
            GroupRotationTeamIdentityIssueCode.TEAM_NAME_COLLISION,
            (result as GroupRotationTeamRenameResult.Invalid).issues.single().code,
        )
    }

    @Test
    fun duplicateNamesInOnePairingAreRejected() {
        val resolver = resolver(*Array(12) { index -> uuid(index + 1) })
        val result = resolver.planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            entries("A:B", mapOf(1 to "Alpha", 2 to " alpha ")),
            emptyList(),
            emptyList(),
        )

        assertTrue(result is GroupRotationTeamIdentityPlanResult.Invalid)
        assertTrue(
            (result as GroupRotationTeamIdentityPlanResult.Invalid).issues.any {
                it.code == GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME
            },
        )
    }

    @Test
    fun threeGroupCapacityAllowsEighteenAndRejectsNineteen() {
        val resolver = resolver(*Array(19) { index -> uuid(index + 1) })
        val tournament = tournament(3, listOf("A:B"))
        var teams = emptyList<GroupRotationTeamIdentity>()
        repeat(18) { index ->
            teams = (
                resolver.registerUnassignedTeam(tournament, "Team ${index + 1}", teams)
                    as GroupRotationTeamRegistrationResult.Registered
                ).teams
        }

        val result = resolver.registerUnassignedTeam(tournament, "Team 19", teams)

        assertEquals(18, teams.size)
        assertTrue(result is GroupRotationTeamRegistrationResult.Invalid)
        assertTrue(
            (result as GroupRotationTeamRegistrationResult.Invalid).issues.any {
                it.code == GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED
            },
        )
    }

    @Test
    fun fourGroupCapacityAllowsTwentyFourAndRejectsTwentyFive() {
        val resolver = resolver(*Array(25) { index -> uuid(index + 1) })
        val tournament = tournament(4, listOf("A:B"))
        var teams = emptyList<GroupRotationTeamIdentity>()
        repeat(24) { index ->
            teams = (
                resolver.registerUnassignedTeam(tournament, "Team ${index + 1}", teams)
                    as GroupRotationTeamRegistrationResult.Registered
                ).teams
        }

        val result = resolver.registerUnassignedTeam(tournament, "Team 25", teams)

        assertEquals(24, teams.size)
        assertTrue(result is GroupRotationTeamRegistrationResult.Invalid)
        assertTrue(
            (result as GroupRotationTeamRegistrationResult.Invalid).issues.any {
                it.code == GroupRotationTeamIdentityIssueCode.TEAM_CAPACITY_EXCEEDED
            },
        )
    }

    @Test
    fun generatedIdentityDoesNotDependOnNumericSlotsGroupsLobbyPositionsOrNames() {
        val generated = mutableListOf<String>()
        val resolver = GroupRotationTeamIdentityResolver(
            uuidGenerator = GroupRotationTeamUuidGenerator {
                uuid(9000 + generated.size).also(generated::add)
            },
        )
        val result = resolver.planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            entries("A:B", mapOf(12 to "Lobby Twelve")),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(uuid(9011), result.plan.assignments.first { it.lobbySlotNumber == 12 }.teamUUID)
        assertTrue(result.plan.teams.none { it.teamUUID == "12" || it.teamUUID == "A" })
        assertEquals(12, generated.size)
    }

    @Test
    fun blankUnassignedRowsAreSkippedAndDoNotCreatePlaceholderTeams() {
        val result = resolver(uuid(1)).planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(2, ""), entry(6, "Alpha"), entry(10, "   ")),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals(1, result.plan.teams.size)
        assertEquals(1, result.plan.assignments.size)
        assertEquals(6, result.plan.assignments.single().lobbySlotNumber)
    }

    @Test
    fun sparseEntriesAndTwelveRowsWithBlanksAreAccepted() {
        val tournament = tournament(3, listOf("A:B", "A:C"))
        val sparse = resolver(uuid(1)).planPairing(
            tournament,
            pairing("A:B"),
            listOf(entry(3, "Alpha")),
            emptyList(),
            emptyList(),
        )
        val fullWithBlanks = resolver(uuid(1)).planPairing(
            tournament,
            pairing("A:C"),
            (1..12).map { lobbySlot ->
                if (lobbySlot == 8) entry(lobbySlot, "Bravo") else entry(lobbySlot, "")
            },
            emptyList(),
            emptyList(),
        )

        assertEquals(1, (sparse as GroupRotationTeamIdentityPlanResult.Planned).plan.assignments.size)
        assertEquals(1, (fullWithBlanks as GroupRotationTeamIdentityPlanResult.Planned).plan.assignments.size)
    }

    @Test
    fun completelyUnconfiguredPairingWithZeroEntriesIsAccepted() {
        val result = resolver().planPairing(
            tournament(3, listOf("A:B", "A:C")),
            pairing("A:C"),
            emptyList(),
            emptyList(),
            emptyList(),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertTrue(result.plan.teams.isEmpty())
        assertTrue(result.plan.assignments.isEmpty())
    }

    @Test
    fun oneFiveAndTwelveOccupiedPositionsAreAccepted() {
        listOf(1, 5, 12).forEach { occupiedCount ->
            val result = resolver(*Array(12) { index -> uuid(index + 1) }).planPairing(
                tournament(3, listOf("A:B")),
                pairing("A:B"),
                (1..occupiedCount).map { entry(it, "Team $it") },
                emptyList(),
                emptyList(),
            ) as GroupRotationTeamIdentityPlanResult.Planned

            assertEquals(occupiedCount, result.plan.assignments.size)
            assertEquals(occupiedCount, result.plan.teams.size)
        }
    }

    @Test
    fun duplicateUuidAssignmentsWithinPairingAreRejected() {
        val team = identity(1, "Alpha")
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(2, "Alpha", team.teamUUID)),
            listOf(team),
            listOf(assignment("A:B", 1, team.teamUUID)),
        )

        assertIssue(result, GroupRotationTeamIdentityIssueCode.DUPLICATE_TEAM_IN_PAIRING)
    }

    @Test
    fun savingOnePairingPreservesUnrelatedPairingMappingsAndOmittedAssignments() {
        val first = identity(1, "Alpha")
        val second = identity(2, "Bravo")
        val originalFirst = assignment("A:B", 1, first.teamUUID)
        val unrelated = assignment("A:C", 7, second.teamUUID)
        val result = resolver(uuid(3)).planPairing(
            tournament(3, listOf("A:B", "A:C")),
            pairing("A:B"),
            listOf(entry(4, "Charlie")),
            listOf(first, second),
            listOf(originalFirst, unrelated),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertTrue(originalFirst in result.plan.assignments)
        assertTrue(unrelated in result.plan.assignments)
        assertTrue(result.plan.assignments.any { it.pairingKey == pairing("A:B") && it.lobbySlotNumber == 4 })
        assertEquals(3, result.plan.assignments.size)
    }

    @Test
    fun conflictingRenameIsRejectedAndExplicitClearingIsPlanned() {
        val alpha = identity(1, "Alpha")
        val bravo = identity(2, "Bravo")
        val assignments = listOf(assignment("A:B", 1, alpha.teamUUID))
        val rename = resolver().renameTeam(
            tournament(3, listOf("A:B")),
            alpha.teamUUID,
            "BRAVO",
            listOf(alpha, bravo),
            assignments,
        )
        val clear = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(1, "")),
            listOf(alpha),
            assignments,
        )

        assertIssue(rename, GroupRotationTeamIdentityIssueCode.TEAM_NAME_COLLISION)
        assertTrue(clear is GroupRotationTeamIdentityPlanResult.Planned)
        val clearedAssignment = (clear as GroupRotationTeamIdentityPlanResult.Planned).plan.assignments.single()
        assertEquals(alpha.teamUUID, clearedAssignment.teamUUID)
        assertTrue(clearedAssignment.nameCleared)
    }

    @Test
    fun reenteringNameAfterClearReusesUuidAndClearsNameClearedFlag() {
        val alpha = identity(1, "Alpha")
        val cleared = assignment("A:B", 1, alpha.teamUUID).copy(nameCleared = true)

        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(1, "Gamma")),
            listOf(alpha),
            listOf(cleared),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        val assignment = result.plan.assignments.single()
        assertEquals(alpha.teamUUID, assignment.teamUUID)
        assertFalse(assignment.nameCleared)
        assertEquals("Gamma", result.plan.teams.single().displayName)
    }

    @Test
    fun clearingAllOccurrencesForgetsActiveNameForFutureRegistration() {
        val resolver = resolver(uuid(2))
        val alpha = identity(1, "Alpha")
        val saved = listOf(
            assignment("A:B", 1, alpha.teamUUID),
            assignment("A:C", 2, alpha.teamUUID),
        )

        val result = resolver.planPairings(
            tournament(3, listOf("A:B", "A:C")),
            mapOf(
                pairing("A:B") to listOf(entry(1, ""), entry(2, "Alpha")),
                pairing("A:C") to listOf(entry(2, "")),
            ),
            listOf(alpha),
            saved,
        ) as GroupRotationTeamIdentityPlanResult.Planned

        val cleared = result.plan.assignments.first { it.pairingKey == pairing("A:B") && it.lobbySlotNumber == 1 }
        assertTrue(cleared.nameCleared)
        assertNotEquals(alpha.teamUUID, result.plan.assignments.first {
            it.pairingKey == pairing("A:B") && it.lobbySlotNumber == 2
        }.teamUUID)
        assertEquals("", result.plan.teams.first { it.teamUUID == alpha.teamUUID }.displayName)
    }

    @Test
    fun simultaneousNameSwapIsValidatedAgainstFinalState() {
        val alpha = identity(1, "Alpha")
        val bravo = identity(2, "Bravo")
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(
                entry(1, "Bravo"),
                entry(2, "Alpha"),
            ),
            listOf(alpha, bravo),
            listOf(
                assignment("A:B", 1, alpha.teamUUID),
                assignment("A:B", 2, bravo.teamUUID),
            ),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals("Bravo", result.plan.teams.first { it.teamUUID == alpha.teamUUID }.displayName)
        assertEquals("Alpha", result.plan.teams.first { it.teamUUID == bravo.teamUUID }.displayName)
    }

    @Test
    fun sameUuidRenameAcrossPairingsPreservesClearedOccurrence() {
        val alpha = identity(1, "Alpha")
        val result = resolver().planPairing(
            tournament(3, listOf("A:B", "A:C")),
            pairing("A:B"),
            listOf(entry(1, "Gamma")),
            listOf(alpha),
            listOf(
                assignment("A:B", 1, alpha.teamUUID),
                assignment("A:C", 2, alpha.teamUUID).copy(nameCleared = true),
            ),
        ) as GroupRotationTeamIdentityPlanResult.Planned

        assertEquals("Gamma", result.plan.teams.single().displayName)
        assertTrue(result.plan.assignments.first { it.pairingKey == pairing("A:C") }.nameCleared)
        assertEquals(alpha.teamUUID, result.plan.assignments.first { it.pairingKey == pairing("A:C") }.teamUUID)
    }

    @Test
    fun clearedAssignmentHydratesAsBlankWithoutChangingRegisteredIdentity() {
        val alpha = identity(1, "Alpha")
        val snapshot = GroupRotationTeamIdentityLocalSnapshot(
            teams = listOf(alpha),
            assignments = listOf(assignment("A:B", 1, alpha.teamUUID).copy(nameCleared = true)),
        )

        val candidate = snapshot.toSetupCandidate(tournament(3, listOf("A:B")))

        assertEquals("", candidate.entries.single().teamName)
        assertEquals(alpha.displayName, snapshot.teams.single().displayName)
        assertEquals(alpha.teamUUID, snapshot.assignments.single().teamUUID)
    }

    @Test
    fun malformedGeneratedAndExistingUuidsAreRejected() {
        val generated = GroupRotationTeamIdentityResolver(
            uuidGenerator = GroupRotationTeamUuidGenerator { "not-a-uuid" },
        ).registerUnassignedTeam(
            tournament(3, listOf("A:B")),
            "Alpha",
            emptyList(),
        )
        val existing = resolver().registerUnassignedTeam(
            tournament(3, listOf("A:B")),
            "Bravo",
            listOf(identityWithUuid("not-a-uuid", "Alpha")),
        )

        assertIssue(generated, GroupRotationTeamIdentityIssueCode.UUID_GENERATION_FAILED)
        assertIssue(existing, GroupRotationTeamIdentityIssueCode.INVALID_TEAM_UUID)
    }

    @Test
    fun unassignedBlankWithUuidIsRejected() {
        val result = resolver().planPairing(
            tournament(3, listOf("A:B")),
            pairing("A:B"),
            listOf(entry(4, "", uuid(1))),
            listOf(identity(1, "Alpha")),
            emptyList(),
        )

        assertIssue(result, GroupRotationTeamIdentityIssueCode.UNASSIGNED_SLOT_WITH_UUID)
    }

    private fun assertIssue(
        result: Any,
        code: GroupRotationTeamIdentityIssueCode,
    ) {
        val issues = when (result) {
            is GroupRotationTeamRegistrationResult.Invalid -> result.issues
            is GroupRotationTeamIdentityPlanResult.Invalid -> result.issues
            is GroupRotationTeamRenameResult.Invalid -> result.issues
            else -> error("Expected an invalid identity result.")
        }
        assertTrue("Expected issue $code, got $issues", issues.any { it.code == code })
    }

    private fun resolver(vararg ids: String): GroupRotationTeamIdentityResolver {
        var index = 0
        return GroupRotationTeamIdentityResolver(
            uuidGenerator = GroupRotationTeamUuidGenerator { ids[index++] },
        )
    }

    private fun identity(number: Int, name: String): GroupRotationTeamIdentity =
        identityWithUuid(uuid(number), name)

    private fun identityWithUuid(teamUUID: String, name: String): GroupRotationTeamIdentity =
        GroupRotationTeamIdentity(
            tournamentId = TOURNAMENT_ID,
            teamUUID = teamUUID,
            displayName = name,
            normalizedName = GroupRotationTeamIdentityNormalizer().normalize(name),
        )

    private fun uuid(number: Int): String =
        "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"

    private fun assignment(
        pairingKey: String,
        lobbySlotNumber: Int,
        teamUUID: String,
    ): GroupRotationPairingAssignment = GroupRotationPairingAssignment(
        tournamentId = TOURNAMENT_ID,
        pairingKey = pairing(pairingKey),
        lobbySlotNumber = lobbySlotNumber,
        teamUUID = teamUUID,
    )

    private fun entry(
        lobbySlotNumber: Int,
        displayName: String,
        teamUUID: String? = null,
    ): GroupRotationPairingTeamIdentityEntry = GroupRotationPairingTeamIdentityEntry(
        lobbySlotNumber = lobbySlotNumber,
        displayName = displayName,
        teamUUID = teamUUID,
    )

    private fun entries(
        pairingKey: String,
        namedLobbySlots: Map<Int, String>,
        teams: List<GroupRotationTeamIdentity> = emptyList(),
        assignments: List<GroupRotationPairingAssignment> = emptyList(),
    ): List<GroupRotationPairingTeamIdentityEntry> {
        val existingByLobby = assignments
            .filter { it.pairingKey == pairing(pairingKey) }
            .associateBy { it.lobbySlotNumber }
        return (1..12).map { lobbySlotNumber ->
            val name = namedLobbySlots[lobbySlotNumber] ?: "Team $lobbySlotNumber"
            val existingUuid = existingByLobby[lobbySlotNumber]?.teamUUID
                ?: teams.firstOrNull {
                    it.normalizedName == GroupRotationTeamIdentityNormalizer().normalize(name)
                }?.teamUUID
            GroupRotationPairingTeamIdentityEntry(lobbySlotNumber, name, existingUuid)
        }
    }

    private fun pairing(key: String): GroupPairing = GroupPairing.fromCanonicalKey(key)

    private fun tournament(groupCount: Int, pairings: List<String>): Tournament = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = pairings.map(::pairing),
    )

    private companion object {
        const val TOURNAMENT_ID = "identity-test"
    }
}
