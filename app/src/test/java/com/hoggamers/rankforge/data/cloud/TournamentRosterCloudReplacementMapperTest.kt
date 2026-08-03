package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentRosterCloudReplacementMapperTest {
    @Test
    fun mapsAllSlotsAndRosterPlayersToStableReplacementPayloads() {
        val result = TournamentRosterCloudReplacementMapper.map(snapshot(), OWNER_ID)

        assertTrue(result is TournamentRosterCloudReplacementMappingResult.Success)
        val payloads = (result as TournamentRosterCloudReplacementMappingResult.Success).payloads
        assertEquals(12, payloads.teamSlots.size)
        assertEquals(TeamSlot.SLOT_NUMBERS.toList(), payloads.teamSlots.map { it.slotNumber })
        assertEquals("draft", payloads.teamSlots.single { it.slotNumber == 1 }.status)
        assertEquals("Alpha Player", payloads.players.single().normalizedName)
        assertEquals(
            TournamentCloudIdentity.playerId(java.util.UUID.fromString(TOURNAMENT_ID), 1, 1),
            payloads.players.single().id,
        )
    }

    @Test
    fun rejectsMissingOrDuplicateSlots() {
        val missing = snapshot().copy(slots = snapshot().slots.dropLast(1))
        val duplicate = snapshot().copy(slots = snapshot().slots.dropLast(1) + TeamSlot.create(TOURNAMENT_ID, 1, "Duplicate"))

        assertEquals(
            TournamentRosterCloudReplacementMappingResult.Invalid,
            TournamentRosterCloudReplacementMapper.map(missing, OWNER_ID),
        )
        assertEquals(
            TournamentRosterCloudReplacementMappingResult.Invalid,
            TournamentRosterCloudReplacementMapper.map(duplicate, OWNER_ID),
        )
    }

    @Test
    fun rejectsPlayersWithWrongTournamentOrSlot() {
        val invalid = snapshot().copy(
            rosters = mapOf(1 to listOf(RosterPlayer("other-tournament", 2, "Wrong Player"))),
        )

        assertEquals(
            TournamentRosterCloudReplacementMappingResult.Invalid,
            TournamentRosterCloudReplacementMapper.map(invalid, OWNER_ID),
        )
    }

    private fun snapshot() = TournamentRosterCloudReplacement(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Roster Cup",
            date = LocalDate.of(2026, 8, 3),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
        ),
        slots = TeamSlot.SLOT_NUMBERS.map { TeamSlot.create(TOURNAMENT_ID, it, "Team $it") },
        rosters = mapOf(1 to listOf(RosterPlayer(TOURNAMENT_ID, 1, "Alpha Player"))),
        expectedCloudRevision = 2,
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val OWNER_ID = "22222222-2222-2222-2222-222222222222"
    }
}
