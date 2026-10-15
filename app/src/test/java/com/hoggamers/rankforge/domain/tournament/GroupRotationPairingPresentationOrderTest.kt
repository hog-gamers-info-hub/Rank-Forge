package com.hoggamers.rankforge.domain.tournament

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupRotationPairingPresentationOrderTest {
    @Test
    fun threeGroupDefaultsUseProductOrderThenSelectedExtras() {
        val tournament = tournament(
            groupCount = 3,
            pairings = listOf("A:C", "B:C", "A:B"),
        )

        assertEquals(
            listOf("A:B", "B:C", "A:C"),
            orderedGroupRotationPairings(tournament).map { it.canonicalKey },
        )
    }

    @Test
    fun fourGroupDefaultsUseProductOrderThenSelectedExtras() {
        val tournament = tournament(
            groupCount = 4,
            pairings = listOf("A:C", "A:D", "C:D", "A:B", "B:D", "B:C"),
        )

        assertEquals(
            listOf("A:B", "B:C", "C:D", "A:D", "A:C", "B:D"),
            orderedGroupRotationPairings(tournament).map { it.canonicalKey },
        )
    }

    private fun tournament(groupCount: Int, pairings: List<String>): Tournament = Tournament(
        id = "presentation-order",
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = pairings.map(GroupPairing::fromCanonicalKey),
    )
}
