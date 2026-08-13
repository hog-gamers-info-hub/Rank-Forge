package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncSnapshot
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftMatchCloudSyncMapperTest {
    @Test
    fun mapsOnlyDraftMatchesAndAvailableResultRowsWithStableCloudIds() {
        val result = DraftMatchCloudSyncMapper.map(snapshot()) as DraftMatchCloudSyncMappingResult.Success
        val payloads = result.payloads
        val expectedMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$TOURNAMENT_ID:draft-match".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedTeamSlotId = UUID.nameUUIDFromBytes(
            "rank-forge:team-slot:$TOURNAMENT_ID:1".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        val expectedResultId = UUID.nameUUIDFromBytes(
            "rank-forge:match-result:$expectedMatchId:$expectedTeamSlotId".toByteArray(StandardCharsets.UTF_8),
        ).toString()

        assertEquals(1, payloads.matches.size)
        assertEquals(expectedMatchId, payloads.matches.single().id)
        assertEquals("draft", payloads.matches.single().status)
        assertEquals(2, payloads.matchResults.size)
        assertEquals(expectedResultId, payloads.matchResults.single { it.teamSlotId == expectedTeamSlotId }.id)
        assertEquals(2, payloads.matchResults.single { it.teamSlotId == expectedTeamSlotId }.placement)
        assertEquals(4, payloads.matchResults.single { it.teamSlotId == expectedTeamSlotId }.kills)
        assertEquals(null, payloads.matchResults.single { it.teamSlotId != expectedTeamSlotId }.placement)
        assertEquals(7, payloads.matchResults.single { it.teamSlotId != expectedTeamSlotId }.kills)
        assertTrue(payloads.matchResults.all { it.reviewStatus == "draft" && it.source == "manual" })
    }

    @Test
    fun repeatedMappingProducesIdenticalPayloadsWithoutDuplicateIntent() {
        val first = DraftMatchCloudSyncMapper.map(snapshot())
        val second = DraftMatchCloudSyncMapper.map(snapshot())

        assertEquals(first, second)
    }

    @Test
    fun mapsNewEmptyDraftToOneMatchPayloadWithoutResultRows() {
        val emptyDraft = snapshot().copy(
            matches = listOf(
                snapshot().matches.first().copy(
                    placements = emptyList(),
                    kills = emptyList(),
                ),
            ),
        )

        val result = DraftMatchCloudSyncMapper.map(emptyDraft) as DraftMatchCloudSyncMappingResult.Success

        assertEquals(1, result.payloads.matches.size)
        assertEquals(0, result.payloads.matchResults.size)
        assertEquals("draft", result.payloads.matches.single().status)
    }

    @Test
    fun rejectsInvalidTournamentUuidAndInvalidDraftResultRows() {
        val invalidTournament = snapshot().copy(
            tournament = snapshot().tournament.copy(id = "not-a-uuid"),
        )
        val invalidResult = snapshot().copy(
            matches = listOf(
                snapshot().matches.first().copy(kills = listOf(MatchKill(13, -1))),
            ),
        )

        assertEquals(DraftMatchCloudSyncMappingResult.Invalid, DraftMatchCloudSyncMapper.map(invalidTournament))
        assertEquals(DraftMatchCloudSyncMappingResult.Invalid, DraftMatchCloudSyncMapper.map(invalidResult))
    }

    private fun snapshot() = DraftMatchCloudSyncSnapshot(
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
                id = "draft-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
                placements = listOf(MatchPlacement(1, 2)),
                kills = listOf(MatchKill(1, 4), MatchKill(2, 7)),
            ),
            Match(
                id = "finalized-match",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 2,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Purgatory",
                status = MatchStatus.FINALIZED,
                placements = listOf(MatchPlacement(1, 1)),
                kills = listOf(MatchKill(1, 9)),
            ),
        ),
    )

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
    }
}
