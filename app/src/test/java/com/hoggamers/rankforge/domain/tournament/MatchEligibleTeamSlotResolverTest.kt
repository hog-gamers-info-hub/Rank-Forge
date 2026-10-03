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
    private val slots = tournament.formatDerivedSlots()

    @Test
    fun resolvesOnlyTheTwoSelectedGroupsToTwelvePermanentSlots() {
        val match = match(GroupPairing(TournamentGroup.A, TournamentGroup.C))

        assertEquals(
            (1..6).toSet() + (13..18).toSet(),
            resolver.resolve(tournament, slots, match),
        )
    }

    @Test
    fun resolvesTheTwoHighestGroupsForFourGroupTournament() {
        val fourGroupTournament = tournament.copy(
            groupCount = 4,
            selectedGroupPairings = listOf(GroupPairing(TournamentGroup.C, TournamentGroup.D)),
        )

        assertEquals(
            (13..24).toSet(),
            resolver.resolve(
                fourGroupTournament,
                fourGroupTournament.formatDerivedSlots(),
                matchFor(fourGroupTournament.id).copy(
                    groupPairing = GroupPairing(TournamentGroup.C, TournamentGroup.D),
                ),
            ),
        )
    }

    @Test
    fun rejectsDuplicatePersistedSlotIdentity() {
        val duplicateSlots = slots + slots.first()

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(
                tournament,
                duplicateSlots,
                match(GroupPairing(TournamentGroup.A, TournamentGroup.C)),
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
}
