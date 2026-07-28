package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class FinalizedMatchCloudSyncMapperTest {
    @Test
    fun mapsOnlyFinalizedMatchesAndAllConfirmedResultRowsWithStableIds() {
        val result = FinalizedMatchCloudSyncMapper.map(snapshot()) as FinalizedMatchCloudSyncMappingResult.Success
        val payloads = result.payloads
        val expectedMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$TOURNAMENT_ID:finalized-match".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedTeamSlotId = UUID.nameUUIDFromBytes(
            "rank-forge:team-slot:$TOURNAMENT_ID:1".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedResultId = UUID.nameUUIDFromBytes(
            "rank-forge:match-result:$expectedMatchId:$expectedTeamSlotId".toByteArray(StandardCharsets.UTF_8),
        ).toString()

        assertEquals(1, payloads.matches.size)
        assertEquals(expectedMatchId, payloads.matches.single().id)
        assertEquals("finalized", payloads.matches.single().status)
        assertEquals(12, payloads.matchResults.size)
        assertEquals(expectedResultId, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.id)
        assertEquals(1, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.placement)
        assertEquals(0, payloads.matchResults.first { it.teamSlotId == expectedTeamSlotId }.kills)
        assertEquals("confirmed", payloads.matchResults.first().reviewStatus)
    }

    @Test
    fun repeatedMappingProducesIdenticalPayloadsWithoutDuplicateIntent() {
        assertEquals(
            FinalizedMatchCloudSyncMapper.map(snapshot()),
            FinalizedMatchCloudSyncMapper.map(snapshot()),
        )
    }

    @Test
    fun rejectsInvalidTournamentUuidAndIncompleteFinalizedRows() {
        val invalidId = snapshot().copy(tournament = snapshot().tournament.copy(id = "not-a-uuid"))
        val incomplete = snapshot().copy(
            matches = listOf(snapshot().matches.first().copy(placements = emptyList())),
        )

        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, FinalizedMatchCloudSyncMapper.map(invalidId))
        assertEquals(FinalizedMatchCloudSyncMappingResult.Invalid, FinalizedMatchCloudSyncMapper.map(incomplete))
    }

    private fun snapshot() = FinalizedMatchCloudSyncSnapshot(
        tournament = Tournament(
            id = TOURNAMENT_ID,
            name = "Summer Cup",
            date = LocalDate.of(2026, 7, 24),
            organizerName = "Organizer",
            organizerContactNumber = "123",
            status = TournamentStatus.CONFIRMED,
        ),
        matches = listOf(
            Match(
                id = "finalized-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.FINALIZED,
                placements = TeamSlot.SLOT_NUMBERS.map { MatchPlacement(it, it) },
                kills = TeamSlot.SLOT_NUMBERS.map { MatchKill(it, it - 1) },
            ),
            Match(
                id = "draft-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Purgatory",
                status = MatchStatus.DRAFT,
            ),
        ),
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
