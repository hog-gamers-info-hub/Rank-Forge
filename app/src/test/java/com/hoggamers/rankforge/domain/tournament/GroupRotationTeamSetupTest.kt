package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationTeamSetupTest {
    private val validator = GroupRotationTeamSetupValidator()
    private val planner = GroupRotationTeamSetupPlanner()

    @Test
    fun normalizerCleansDisplayNameAndPreservesPunctuationIdentity() {
        val normalizer = GroupRotationTeamIdentityNormalizer()

        assertEquals("TEAM-X TEAM_X", normalizer.cleanDisplayName("  TEAM-X   TEAM_X  "))
        assertEquals("team-x team_x", normalizer.normalize("  TEAM-X   TEAM_X  "))
        assertTrue(normalizer.normalize("TEAM-X") != normalizer.normalize("TEAM X"))
        assertTrue(normalizer.normalize("TEAM-X") != normalizer.normalize("TEAM_X"))
    }

    @Test
    fun groupRotationDerivedSlotsAreSequentialAndUnguardedByGroupMetadata() {
        val groupRotation = tournament(groupCount = 3, pairings = listOf("A:B"))
        val standard = Tournament(
            id = TOURNAMENT_ID,
            name = "Standard",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )

        assertEquals((1..18).toList(), groupRotation.formatDerivedSlots().map { it.slotNumber })
        assertTrue(groupRotation.formatDerivedSlots().all { it.group == null })
        assertEquals(12, standard.formatDerivedSlots().size)
        assertTrue(TeamSlot.create(TOURNAMENT_ID, 18).group == null)
    }

    @Test
    fun validatorAcceptsOneSubmittedSelectedPairingWithoutRequiringOtherSelectedPairings() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B", "A:C"))

        val result = validator.validate(
            tournament,
            completeCandidate(tournament, "A:B"),
        )

        assertTrue(result is GroupRotationTeamSetupValidationResult.Valid)
        assertEquals(
            setOf("A:B"),
            (result as GroupRotationTeamSetupValidationResult.Valid)
                .entries
                .map { it.pairing.canonicalKey }
                .toSet(),
        )
    }

    @Test
    fun validatorRejectsAnEntirelyEmptyCandidate() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B", "A:C"))

        val result = validator.validate(
            tournament,
            GroupRotationTeamSetupCandidate(TOURNAMENT_ID, emptyList()),
        ) as GroupRotationTeamSetupValidationResult.Invalid

        assertTrue(result.issues.any { it.code == GroupRotationTeamSetupIssueCode.EMPTY_CANDIDATE })
        assertTrue(result.issues.none { it.code == GroupRotationTeamSetupIssueCode.MISSING_PAIRING })
    }

    @Test
    fun threeGroupPairingCandidateValidatesAtEighteenUniqueTeams() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B", "A:C", "B:C"))
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            listOf("A:B", "A:C", "B:C").flatMapIndexed { pairingIndex, pairingKey ->
                (1..12).map { lobbySlot ->
                    val name = when (pairingIndex) {
                        0 -> "Team $lobbySlot"
                        1 -> if (lobbySlot <= 6) "Team $lobbySlot" else "Team ${lobbySlot + 6}"
                        else -> if (lobbySlot <= 6) "Team ${lobbySlot + 6}" else "Team ${lobbySlot + 6}"
                    }
                    entry(pairingKey, lobbySlot, name)
                }
            },
        )

        val result = validator.validate(tournament, candidate)

        assertTrue(result is GroupRotationTeamSetupValidationResult.Valid)
        assertEquals(18, (result as GroupRotationTeamSetupValidationResult.Valid).normalizedIdentities.size)
    }

    @Test
    fun fourGroupPairingCandidateValidatesAtTwentyFourUniqueTeams() {
        val tournament = tournament(groupCount = 4, pairings = listOf("A:B", "A:C", "B:C", "C:D"))
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            listOf("A:B", "A:C", "B:C", "C:D").flatMapIndexed { pairingIndex, pairingKey ->
                (1..12).map { lobbySlot ->
                    val teamNumber = ((pairingIndex * 6 + lobbySlot - 1) % 24) + 1
                    entry(pairingKey, lobbySlot, "Team $teamNumber")
                }
            },
        )

        val result = validator.validate(tournament, candidate)

        assertTrue(result is GroupRotationTeamSetupValidationResult.Valid)
        assertEquals(24, (result as GroupRotationTeamSetupValidationResult.Valid).normalizedIdentities.size)
    }

    @Test
    fun validatorRejectsMissingExtraPairingsInvalidLobbyShapeAndDuplicateIdentity() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B", "A:C"))
        val entries = buildList {
            addAll((1..11).map { entry("A:B", it, "Team $it") })
            add(entry("A:B", 11, "Team duplicate"))
            addAll((1..12).map { entry("B:C", it, "Other $it") })
            addAll((1..12).map { entry("A:C", it, if (it == 1) "   " else "A $it") })
        }

        val result = validator.validate(
            tournament,
            GroupRotationTeamSetupCandidate(TOURNAMENT_ID, entries),
        ) as GroupRotationTeamSetupValidationResult.Invalid

        assertTrue(result.issues.any { it.code == GroupRotationTeamSetupIssueCode.EXTRA_PAIRING })
        assertTrue(result.issues.any { it.code == GroupRotationTeamSetupIssueCode.DUPLICATE_LOBBY_SLOT })
        assertTrue(result.issues.any { it.code == GroupRotationTeamSetupIssueCode.BLANK_TEAM_NAME })
    }

    @Test
    fun sameIdentityAcrossPairingsGetsOnePermanentSlot() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B", "A:C"))
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            listOf(
                entry("A:B", 1, "  Alpha  "),
                entry("A:B", 2, "B2"),
                entry("A:C", 1, "alpha"),
                entry("A:C", 2, "C2"),
            ).plus(
                (3..12).map { entry("A:B", it, "B$it") },
            ).plus(
                (3..7).map { entry("A:C", it, "C$it") },
            ).plus(
                (8..12).map { entry("A:C", it, "B${it - 6}") },
            ),
        )
        val validated = validator.validate(tournament, candidate) as GroupRotationTeamSetupValidationResult.Valid
        val plan = planner.plan(tournament, emptyList(), validated) as GroupRotationTeamSetupPlanningResult.Planned

        assertEquals(24, plan.plan.lobbySlotMappings.size)
        assertEquals(1, plan.plan.lobbySlotMappings.first { it.pairing.canonicalKey == "A:B" }.teamSlotNumber)
        assertEquals(1, plan.plan.lobbySlotMappings.first { it.pairing.canonicalKey == "A:C" }.teamSlotNumber)
        assertEquals("Alpha", plan.plan.teamSlots.first { it.slotNumber == 1 }.teamName)
    }

    @Test
    fun validatorReportsLobbySlotZeroAsInvalidLobbySlot() {
        assertInvalidLobbySlot(0)
    }

    @Test
    fun validatorReportsLobbySlotThirteenAsInvalidLobbySlot() {
        assertInvalidLobbySlot(13)
    }

    @Test
    fun newIdentitiesUseLowestAvailableCanonicalSlotsAndPreservesAbsentIdentities() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val existing = tournament.formatDerivedSlots().map { slot ->
            when (slot.slotNumber) {
                5 -> slot.copy(teamName = "Existing")
                18 -> slot.copy(teamName = "Obsolete", group = TournamentGroup.C)
                else -> slot
            }
        }
        val candidate = completeCandidate(tournament, "A:B", firstName = "Existing")
        val validated = validator.validate(tournament, candidate) as GroupRotationTeamSetupValidationResult.Valid
        val plan = planner.plan(tournament, existing, validated) as GroupRotationTeamSetupPlanningResult.Planned

        assertEquals(5, plan.plan.lobbySlotMappings.first().teamSlotNumber)
        assertEquals("Team 2", plan.plan.teamSlots.first { it.slotNumber == 1 }.teamName)
        assertEquals("Existing", plan.plan.teamSlots.first { it.slotNumber == 5 }.teamName)
        assertEquals("Obsolete", plan.plan.teamSlots.first { it.slotNumber == 18 }.teamName)
        assertEquals(TournamentGroup.C, plan.plan.teamSlots.first { it.slotNumber == 18 }.group)
        assertEquals(
            listOf(5, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12),
            plan.plan.lobbySlotMappings.map { it.teamSlotNumber },
        )
    }

    @Test
    fun arbitraryLobbyPositionUsesExistingIdentityInsteadOfLobbyPosition() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val existing = tournament.formatDerivedSlots().map { slot ->
            when (slot.slotNumber) {
                2 -> slot.copy(teamName = "Lobby One Team")
                13 -> slot.copy(teamName = "Lobby Seven Team", group = TournamentGroup.C)
                else -> slot
            }
        }
        val candidate = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            listOf(
                entry("A:B", 1, "Lobby One Team"),
                entry("A:B", 7, "Lobby Seven Team"),
            ).plus((2..6).map { entry("A:B", it, "Team $it") })
                .plus((8..12).map { entry("A:B", it, "Team $it") }),
        )
        val validated = validator.validate(tournament, candidate) as GroupRotationTeamSetupValidationResult.Valid
        val plan = planner.plan(tournament, existing, validated) as GroupRotationTeamSetupPlanningResult.Planned

        assertEquals(2, plan.plan.lobbySlotMappings.first { it.lobbySlotNumber == 1 }.teamSlotNumber)
        assertEquals(13, plan.plan.lobbySlotMappings.first { it.lobbySlotNumber == 7 }.teamSlotNumber)
        assertEquals(
            TournamentGroup.C,
            plan.plan.teamSlots.first { it.slotNumber == 13 }.group,
        )
    }

    @Test
    fun ambiguousExistingIdentityFailsClosed() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val existing = tournament.formatDerivedSlots().map { slot ->
            if (slot.slotNumber == 1 || slot.slotNumber == 2) slot.copy(teamName = "Duplicate") else slot
        }
        val validated = validator.validate(
            tournament,
            completeCandidate(tournament, "A:B"),
        ) as GroupRotationTeamSetupValidationResult.Valid

        val result = planner.plan(tournament, existing, validated) as GroupRotationTeamSetupPlanningResult.Invalid

        assertEquals(GroupRotationTeamSetupIssueCode.AMBIGUOUS_EXISTING_IDENTITY, result.issues.single().code)
        assertEquals(listOf(1, 2), result.issues.single().slotNumbers)
    }

    @Test
    fun deterministicPlanIgnoresInputListOrderAndReusesIdentityWhenLobbyOrderChanges() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val existing = tournament.formatDerivedSlots().map { slot ->
            if (slot.slotNumber == 9) slot.copy(teamName = "Alpha") else slot
        }
        val firstCandidate = completeCandidate(tournament, "A:B", firstName = "Alpha")
            .copy(entries = completeCandidate(tournament, "A:B", firstName = "Alpha").entries.reversed())
        val secondCandidate = firstCandidate.copy(entries = firstCandidate.entries.reversed())
        val firstValid = validator.validate(tournament, firstCandidate) as GroupRotationTeamSetupValidationResult.Valid
        val secondValid = validator.validate(tournament, secondCandidate) as GroupRotationTeamSetupValidationResult.Valid
        val firstPlan = planner.plan(tournament, existing, firstValid) as GroupRotationTeamSetupPlanningResult.Planned
        val secondPlan = planner.plan(tournament, existing, secondValid) as GroupRotationTeamSetupPlanningResult.Planned

        assertEquals(firstPlan.plan, secondPlan.plan)
        assertEquals(9, firstPlan.plan.lobbySlotMappings.first().teamSlotNumber)
    }

    @Test
    fun plannerRejectsNewIdentityWhenAllCanonicalSlotsAreOccupied() {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val existing = tournament.formatDerivedSlots().map { slot ->
            slot.copy(teamName = "Existing " + slot.slotNumber)
        }
        val validated = validator.validate(
            tournament,
            completeCandidate(tournament, "A:B"),
        ) as GroupRotationTeamSetupValidationResult.Valid

        val result = planner.plan(tournament, existing, validated)
            as GroupRotationTeamSetupPlanningResult.Invalid

        assertTrue(
            result.issues.any {
                it.code == GroupRotationTeamSetupIssueCode.NO_AVAILABLE_CANONICAL_SLOT
            },
        )
    }

    @Test
    fun standardTournamentIsRejectedWithoutChangingStandardModelRules() {
        val tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Standard",
            stageName = "Stage",
            organizerContactNumber = "123",
            status = TournamentStatus.DRAFT,
        )
        val result = validator.validate(
            tournament,
            GroupRotationTeamSetupCandidate(TOURNAMENT_ID, emptyList()),
        )

        assertTrue(result is GroupRotationTeamSetupValidationResult.Invalid)
        assertTrue(
            (result as GroupRotationTeamSetupValidationResult.Invalid).issues
                .any { it.code == GroupRotationTeamSetupIssueCode.NOT_GROUP_ROTATION },
        )
        assertEquals(12, tournament.formatDerivedSlots().size)
    }

    private fun completeCandidate(
        tournament: Tournament,
        pairingKey: String,
        firstName: String = "Team 1",
    ): GroupRotationTeamSetupCandidate = GroupRotationTeamSetupCandidate(
        TOURNAMENT_ID,
        (1..12).map { lobbySlot ->
            entry(pairingKey, lobbySlot, if (lobbySlot == 1) firstName else "Team $lobbySlot")
        },
    )

    private fun entry(pairingKey: String, lobbySlot: Int, teamName: String): GroupRotationPairingTeamEntry =
        GroupRotationPairingTeamEntry(
            pairing = GroupPairing.fromCanonicalKey(pairingKey),
            lobbySlotNumber = lobbySlot,
            teamName = teamName,
        )

    private fun tournament(groupCount: Int, pairings: List<String>): Tournament = Tournament(
        id = TOURNAMENT_ID,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = pairings.map(GroupPairing::fromCanonicalKey),
    )

    private companion object {
        const val TOURNAMENT_ID = "rotation-test"
    }

    private fun assertInvalidLobbySlot(lobbySlotNumber: Int) {
        val tournament = tournament(groupCount = 3, pairings = listOf("A:B"))
        val candidate = completeCandidate(tournament, "A:B").copy(
            entries = completeCandidate(tournament, "A:B").entries.mapIndexed { index, entry ->
                if (index == 0) entry.copy(lobbySlotNumber = lobbySlotNumber) else entry
            },
        )

        val result = validator.validate(tournament, candidate) as GroupRotationTeamSetupValidationResult.Invalid

        assertTrue(
            result.issues.any {
                it.code == GroupRotationTeamSetupIssueCode.INVALID_LOBBY_SLOT &&
                    it.lobbySlotNumber == lobbySlotNumber
            },
        )
        assertTrue(
            result.issues.none {
                it.code == GroupRotationTeamSetupIssueCode.DUPLICATE_LOBBY_SLOT &&
                    it.lobbySlotNumber == lobbySlotNumber
            },
        )
    }
}

