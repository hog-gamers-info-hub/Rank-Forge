package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionRequest
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectedMatchCorrectionRepositoryTest {
    @Test
    fun mapsTenTeamCorrectionUsingOnlyExistingParticipantIdentity() {
        val parameters = request(match(10), correctedPlacements(10), correctedKills(10)).toParameters()!!

        assertEquals(10, parameters.matchResults.size)
        assertEquals((1..10).toList(), parameters.matchResults.map { it.teamSlotId
            .let { id -> TeamSlot.SLOT_NUMBERS.first { slot -> id == TournamentCloudIdentity.teamSlotId(TOURNAMENT_ID, slot) } }
        })
        assertEquals((1..10).toList(), parameters.matchResults.map { it.placement })
        assertEquals((1..10).toList(), parameters.matchResults.map { it.placement })
        assertEquals(parameters.matchResults.map { it.id }.distinct().size, parameters.matchResults.size)
    }

    @Test
    fun preservesTwelveTeamCorrectionCompatibility() {
        val parameters = request(match(12), correctedPlacements(12), correctedKills(12)).toParameters()!!

        assertEquals(12, parameters.matchResults.size)
        assertEquals((1..12).toList(), parameters.matchResults.map { it.placement })
    }

    @Test
    fun preservesSparseFinalizedParticipantIdentity() {
        val slots = listOf(1, 2, 3, 4, 5, 6, 8, 9, 11, 12)
        val parameters = request(
            match(slots),
            correctedPlacements(slots),
            correctedKills(slots),
        ).toParameters()!!

        assertEquals(slots, parameters.matchResults.map { result ->
            TeamSlot.SLOT_NUMBERS.first { slot ->
                result.teamSlotId == TournamentCloudIdentity.teamSlotId(TOURNAMENT_ID, slot)
            }
        })
        assertEquals((1..10).toList(), parameters.matchResults.map { it.placement })
    }

    @Test
    fun rejectsIncomingParticipantSetDifferentFromFinalizedMatch() {
        assertNull(
            request(
                match(10),
                correctedPlacements(10).dropLast(1),
                correctedKills(10).dropLast(1),
            ).toParameters(),
        )
        assertNull(
            request(
                match(10),
                correctedPlacements(10) + MatchPlacement(11, 11),
                correctedKills(10) + MatchKill(11, 10),
            ).toParameters(),
        )
    }

    @Test
    fun mapsParticipatedToNoShowTransitionWithStableTeamSlotIdentity() {
        val slots = listOf(1, 4, 9)
        val original = slots.mapIndexed { index, slot ->
            if (slot == 9) MatchParticipantResult(slot, MatchParticipationStatus.NO_SHOW, null, 0)
            else MatchParticipantResult(slot, MatchParticipationStatus.PARTICIPATED, index + 1, index)
        }
        val corrected = listOf(
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 2),
            MatchParticipantResult(4, MatchParticipationStatus.NO_SHOW, null, 0),
            MatchParticipantResult(9, MatchParticipationStatus.PARTICIPATED, 2, 5),
        )
        val parameters = request(
            matchWithSnapshot(original),
            placements = corrected.mapNotNull { it.placement?.let { position -> MatchPlacement(it.teamSlotNumber, position) } },
            kills = corrected.map { MatchKill(it.teamSlotNumber, it.kills) },
            participantResults = corrected,
        ).toParameters()!!

        assertEquals(listOf("PARTICIPATED", "NO_SHOW", "PARTICIPATED"), parameters.matchResults.map { it.participationStatus })
        assertEquals(null, parameters.matchResults[1].placement)
        assertEquals(listOf(1, 4, 9), parameters.matchResults.map { result ->
            TeamSlot.SLOT_NUMBERS.first { slot -> result.teamSlotId == TournamentCloudIdentity.teamSlotId(TOURNAMENT_ID, slot) }
        })
    }

    private fun request(
        match: Match,
        placements: List<MatchPlacement>,
        kills: List<MatchKill>,
        participantResults: List<MatchParticipantResult> = emptyList(),
    ) = ProtectedMatchCorrectionRequest(
        tournament = Tournament(
            id = TOURNAMENT_ID.toString(),
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
        ),
        match = match,
        placements = placements,
        kills = kills,
        expectedRevision = 2,
        participantResults = participantResults,
    )

    private fun match(count: Int) = Match(
        id = "match-id",
        tournamentId = TOURNAMENT_ID.toString(),
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = (1..count).map { MatchPlacement(it, it) },
        kills = (1..count).map { MatchKill(it, it - 1) },
    )

    private fun match(slots: List<Int>) = Match(
        id = "match-id",
        tournamentId = TOURNAMENT_ID.toString(),
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = slots.mapIndexed { index, slot -> MatchPlacement(slot, index + 1) },
        kills = slots.mapIndexed { index, slot -> MatchKill(slot, index) },
    )

    private fun correctedPlacements(count: Int) = (1..count).map { slot ->
        MatchPlacement(slot, slot)
    }

    private fun correctedKills(count: Int) = (1..count).map { slot ->
        MatchKill(slot, slot - 1)
    }

    private fun correctedPlacements(slots: List<Int>) = slots.mapIndexed { index, slot ->
        MatchPlacement(slot, index + 1)
    }

    private fun correctedKills(slots: List<Int>) = slots.mapIndexed { index, slot ->
        MatchKill(slot, index)
    }

    private fun matchWithSnapshot(results: List<MatchParticipantResult>) = Match(
        id = "match-id",
        tournamentId = TOURNAMENT_ID.toString(),
        matchNumber = 1,
        date = LocalDate.of(2026, 7, 24),
        mapName = "Bermuda",
        status = MatchStatus.FINALIZED,
        placements = results.mapNotNull { it.placement?.let { position -> MatchPlacement(it.teamSlotNumber, position) } },
        kills = results.map { MatchKill(it.teamSlotNumber, it.kills) },
        participantResults = results,
    )

    private companion object {
        val TOURNAMENT_ID = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
