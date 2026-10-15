package com.hoggamers.rankforge.domain.tournament

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchEligibleTeamSlotResolverTest {
    private val resolver = MatchEligibleTeamSlotResolver()
    private val tournament = Tournament(
        id = "rotation",
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = 3,
        selectedGroupPairings = defaultGroupPairings(3),
    )
    private val slots = tournament.formatDerivedSlots().map { slot ->
        slot.copy(teamName = "Team ${slot.slotNumber}")
    }

    @Test
    fun resolvesOnlyTheTwoSelectedGroupsToTwelvePermanentSlots() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val match = match(pairing)
        val canonicalSlots = listOf(14, 3, 18, 1, 16, 5, 6, 15, 2, 17, 4, 13)

        assertEquals(
            canonicalSlots,
            resolver.resolveContext(tournament, slots, match, mapping(pairing, canonicalSlots))
                .orderedTeams
                .map { it.teamSlotNumber },
        )
        assertEquals(canonicalSlots.toSet(), resolver.resolve(tournament, slots, match, mapping(pairing, canonicalSlots)))
    }

    @Test
    fun resolvesTheTwoHighestGroupsForFourGroupTournament() {
        val fourGroupTournament = tournament.copy(
            id = "four-group",
            groupCount = 4,
            selectedGroupPairings = listOf(GroupPairing(TournamentGroup.C, TournamentGroup.D)),
        )
        val fourGroupSlots = fourGroupTournament.formatDerivedSlots().map { it.copy(teamName = "Team ${it.slotNumber}") }
        val pairing = GroupPairing(TournamentGroup.C, TournamentGroup.D)

        assertEquals(
            (13..24).toSet(),
            resolver.resolve(
                fourGroupTournament,
                fourGroupSlots,
                matchFor(fourGroupTournament.id).copy(
                    groupPairing = pairing,
                ),
                mapping(pairing, (13..24).toList().reversed(), fourGroupTournament.id),
            ),
        )
    }

    @Test
    fun missingMappingFailsClosedInsteadOfUsingLegacyGroups() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(GroupPairing(TournamentGroup.A, TournamentGroup.C)),
            )
        }
    }

    @Test
    fun rejectsDuplicateLobbySlot() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val mappings = mapping(pairing, (1..12).toList()).mapIndexed { index, entry ->
            if (index == 1) entry.copy(lobbySlotNumber = 1) else entry
        }

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mappings,
            )
        }
    }

    @Test
    fun rejectsPairingThatIsNotSelected() {
        val match = match(GroupPairing(TournamentGroup.A, TournamentGroup.B))
        val restrictedTournament = tournament.copy(
            selectedGroupPairings = listOf(GroupPairing(TournamentGroup.B, TournamentGroup.C)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(restrictedTournament, slots, match)
        }
    }

    @Test
    fun missingLobbySlotFailsClosed() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mapping(pairing, (1..11).toList()),
            )
        }
    }

    @Test
    fun extraMappingRowFailsClosed() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mapping(pairing, (1..13).toList()),
            )
        }
    }

    @Test
    fun duplicateCanonicalIdentityFailsClosed() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mapping(pairing, listOf(14, 14) + (15..24).toList()),
            )
        }
    }

    @Test
    fun rejectsWrongTournamentMapping() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mapping(pairing, (1..12).toList(), tournamentId = "another-tournament"),
            )
        }
    }

    @Test
    fun rejectsCanonicalSlotOutsideTournamentFormat() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots,
                match(pairing),
                mapping(pairing, listOf(19) + (1..11).toList()),
            )
        }
    }

    @Test
    fun rejectsMappedCanonicalSlotMissingFromPersistedSlots() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                slots.filterNot { it.slotNumber == 14 },
                match(pairing),
                mapping(pairing, listOf(14, 3, 18, 1, 16, 5, 6, 15, 2, 17, 4, 13)),
            )
        }
    }

    @Test
    fun groupMetadataDoesNotInfluenceV2Resolution() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val remappedSlots = slots.map { slot ->
            if (slot.slotNumber <= TeamSlot.SLOT_NUMBERS.last) slot.copy(group = null) else slot
        }
        val mappings = mapping(pairing, listOf(14, 3, 18, 1, 16, 5, 6, 15, 2, 17, 4, 13))

        assertEquals(
            mappings.map { it.teamSlotNumber }.toSet(),
            resolver.resolve(tournament, remappedSlots, match(pairing), mappings),
        )
    }

    @Test
    fun blankMappedCanonicalTeamFailsClosed() {
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val blankSlots = slots.map { slot ->
            if (slot.slotNumber == 14) slot.copy(teamName = " ") else slot
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                blankSlots,
                match(pairing),
                mapping(pairing, listOf(14, 3, 18, 1, 16, 5, 6, 15, 2, 17, 4, 13)),
            )
        }
    }

    @Test
    fun standardMatchesKeepTheOriginalOneThroughTwelveEligibility() {
        val standard = Tournament(
            id = "standard",
            name = "Standard",
            stageName = "Stage",
            organizerContactNumber = "",
            status = TournamentStatus.DRAFT,
        )

        assertEquals(
            TeamSlot.SLOT_NUMBERS.toSet(),
            resolver.resolve(standard, standard.formatDerivedSlots(), matchFor(standard.id)),
        )
    }

    private fun match(pairing: GroupPairing): Match = matchFor(tournament.id).copy(groupPairing = pairing)

    private fun matchFor(tournamentId: String): Match = Match(
        id = "match",
        tournamentId = tournamentId,
        matchNumber = 1,
        date = LocalDate.of(2026, 10, 3),
        mapName = "",
        status = MatchStatus.DRAFT,
    )

    private fun mapping(
        pairing: GroupPairing,
        canonicalSlots: List<Int>,
        tournamentId: String = tournament.id,
    ): List<GroupRotationPairingLobbySlot> = canonicalSlots.mapIndexed { index, teamSlotNumber ->
        GroupRotationPairingLobbySlot(
            tournamentId = tournamentId,
            pairing = pairing,
            lobbySlotNumber = index + 1,
            teamSlotNumber = teamSlotNumber,
        )
    }
}
