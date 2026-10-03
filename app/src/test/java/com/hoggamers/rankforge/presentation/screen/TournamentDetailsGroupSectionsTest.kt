package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.defaultGroupPairings
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TournamentDetailsGroupSectionsTest {
    @Test
    fun groupDetailsUsesConfiguredPairingOrderAndLocalSectionMembership() {
        val pairings = defaultGroupPairings(3)
        val tournament = Tournament(
            id = "rotation",
            name = "Rotation Cup",
            stageName = "Stage",
            organizerContactNumber = "",
            status = TournamentStatus.CONFIRMED,
            format = TournamentFormat.GROUP_ROTATION,
            groupCount = 3,
            selectedGroupPairings = pairings,
        )
        val matches = listOf(
            match("match-1", 1, pairings[0]),
            match("match-2", 2, pairings[1]),
            match("match-3", 3, pairings[0]),
        )

        val state = tournament.toDetailsItemUiState(tournament.formatDerivedSlots(), matches)

        assertEquals(pairings, state.groupMatchSections.map { it.pairing })
        assertEquals(listOf("match-1", "match-3"), state.groupMatchSections[0].matches.map { it.id })
        assertEquals(listOf("match-2"), state.groupMatchSections[1].matches.map { it.id })
        assertEquals(emptyList<MatchUiState>(), state.groupMatchSections[2].matches)
    }

    private fun match(id: String, number: Int, pairing: GroupPairing) = Match(
        id = id,
        tournamentId = "rotation",
        matchNumber = number,
        date = LocalDate.of(2026, 10, 3),
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
        groupPairing = pairing,
    )
}
