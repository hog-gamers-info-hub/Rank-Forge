package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentFormatTest {
    @Test
    fun existingTournamentDefaultsToStandardWithoutGroupConfiguration() {
        val tournament = tournament()

        assertEquals(TournamentFormat.STANDARD, tournament.format)
        assertNull(tournament.groupCount)
        assertTrue(tournament.selectedGroupPairings.isEmpty())
        assertEquals((1..12).toList(), tournament.formatDerivedSlots().map { it.slotNumber })
        assertTrue(tournament.formatDerivedSlots().all { it.group == null })
    }

    @Test
    fun threeGroupRotationAllocatesSixPermanentSlotsPerGroup() {
        val slots = tournament(
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
        ).formatDerivedSlots()

        assertEquals(18, slots.size)
        assertEquals((1..6).toList(), slots.filter { it.group == TournamentGroup.A }.map { it.slotNumber })
        assertEquals((7..12).toList(), slots.filter { it.group == TournamentGroup.B }.map { it.slotNumber })
        assertEquals((13..18).toList(), slots.filter { it.group == TournamentGroup.C }.map { it.slotNumber })
        assertTrue(slots.none { it.group == TournamentGroup.D })
    }

    @Test
    fun fourGroupRotationAllocatesTwentyFourPermanentSlots() {
        val slots = tournament(
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 4,
        ).formatDerivedSlots()

        assertEquals(24, slots.size)
        assertEquals((19..24).toList(), slots.filter { it.group == TournamentGroup.D }.map { it.slotNumber })
    }

    @Test
    fun reversePairingsShareCanonicalIdentity() {
        val forward = GroupPairing.of(TournamentGroup.A, TournamentGroup.C)
        val reverse = GroupPairing.of(TournamentGroup.C, TournamentGroup.A)

        assertEquals(forward, reverse)
        assertEquals("A:C", forward.canonicalKey)
        assertEquals(forward, GroupPairing.fromCanonicalKey(forward.canonicalKey))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sameGroupPairingIsRejected() {
        GroupPairing.of(TournamentGroup.B, TournamentGroup.B)
    }

    @Test
    fun permanentTeamIdentitiesCanUseGroupSlotsAboveStandardCapacity() {
        assertEquals(
            TournamentGroup.C,
            TeamSlot.create("tournament-id", 13, group = TournamentGroup.C).group,
        )
        MatchParticipantResult(
            teamSlotNumber = 24,
            participationStatus = MatchParticipationStatus.NO_SHOW,
            placement = null,
            kills = 0,
        )
        RosterPlayer.create("tournament-id", 24, "Player")
    }

    private fun tournament(
        format: TournamentFormat = TournamentFormat.STANDARD,
        groupCount: Int? = null,
    ) = Tournament(
        id = "tournament-id",
        name = "Summer Cup",
        stageName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = format,
        groupCount = groupCount,
        selectedGroupPairings = if (format == TournamentFormat.GROUP_ROTATION) {
            defaultGroupPairings(requireNotNull(groupCount))
        } else {
            emptyList()
        },
    )
}
