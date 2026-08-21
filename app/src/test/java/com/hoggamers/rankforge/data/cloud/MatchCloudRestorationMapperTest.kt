package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCloudRestorationMapperTest {
    @Test fun mapsDraftWithBlankMapNameAndPreservesIt() {
        val payload = validPayloads().let { payloads ->
            payloads.copy(matches = payloads.matches.mapIndexed { index, match ->
                if (index == 0) match.copy(mapName = "") else match
            })
        }

        val mapped = MatchCloudRestorationMapper.map(payload) as MatchCloudRestorationMappingResult.Success

        assertEquals("", mapped.value.matches.first().mapName)
    }

    @Test fun mapsDraftWithPartialRowsAndFinalizedWithCompleteRows() {
        val mapped = MatchCloudRestorationMapper.map(validPayloads()) as MatchCloudRestorationMappingResult.Success
        assertEquals(2, mapped.value.matches.size)
        assertEquals(MatchStatus.DRAFT, mapped.value.matches[0].status)
        assertEquals(1, mapped.value.matches[0].kills.size)
        assertEquals(MatchStatus.FINALIZED, mapped.value.matches[1].status)
        assertEquals((1..12).toSet(), mapped.value.matches[1].placements.map { it.position }.toSet())
    }

    @Test fun rejectsInvalidUuidStatusReferenceSlotKillsAndCrossTournamentPayloads() {
        val payload = validPayloads()
        val invalids = listOf(
            payload.copy(tournamentId = "invalid"),
            payload.copy(matches = payload.matches.map { it.copy(status = "unknown") }),
            payload.copy(results = payload.results + payload.results.first().copy(matchId = UUID.randomUUID().toString())),
            payload.copy(results = payload.results.map { it.copy(teamSlotId = UUID.randomUUID().toString()) }),
            payload.copy(results = payload.results.map { it.copy(kills = -1) }),
            payload.copy(matches = payload.matches.map { it.copy(tournamentId = UUID.randomUUID().toString()) }),
        )
        invalids.forEach { assertEquals(MatchCloudRestorationMappingResult.Invalid, MatchCloudRestorationMapper.map(it)) }
    }

    @Test fun rejectsDuplicateFinalizedPlacements() {
        val payload = validPayloads()
        val finalizedId = payload.matches.last().id
        val duplicate = payload.copy(results = payload.results.map { if (it.matchId == finalizedId && it.placement == 12) it.copy(placement = 1) else it })
        assertEquals(MatchCloudRestorationMappingResult.Invalid, MatchCloudRestorationMapper.map(duplicate))
    }

    @Test
    fun restoresSparseParticipantSnapshotWithNoShowStatus() {
        val matchId = UUID.randomUUID().toString()
        val tournamentUuid = UUID.fromString(TOURNAMENT_ID)
        val rows = listOf(
            MatchResultCloudRestorePayload(UUID.randomUUID().toString(), matchId, TournamentCloudIdentity.teamSlotId(tournamentUuid, 1), 1, 4, "PARTICIPATED"),
            MatchResultCloudRestorePayload(UUID.randomUUID().toString(), matchId, TournamentCloudIdentity.teamSlotId(tournamentUuid, 7), 2, 0, "PARTICIPATED"),
            MatchResultCloudRestorePayload(UUID.randomUUID().toString(), matchId, TournamentCloudIdentity.teamSlotId(tournamentUuid, 12), null, 0, "NO_SHOW"),
        )
        val mapped = MatchCloudRestorationMapper.map(
            MatchCloudRestorationPayloads(
                TOURNAMENT_ID,
                listOf(MatchCloudRestorePayload(matchId, TOURNAMENT_ID, 1, "2026-07-24", "Bermuda", "finalized", 1)),
                rows,
                1,
            ),
        ) as MatchCloudRestorationMappingResult.Success

        val restored = mapped.value.matches.single()
        assertEquals(listOf(1, 7, 12), restored.participantResults.map { it.teamSlotNumber })
        assertEquals(MatchParticipationStatus.NO_SHOW, restored.participantResults.last().participationStatus)
        assertEquals(null, restored.participantResults.last().placement)
        assertEquals(listOf(1, 7), restored.kills.map { it.teamSlotNumber })
    }

    private fun validPayloads(): MatchCloudRestorationPayloads {
        val draftId = UUID.nameUUIDFromBytes("draft".toByteArray()).toString()
        val finalId = UUID.nameUUIDFromBytes("final".toByteArray()).toString()
        val matches = listOf(
            MatchCloudRestorePayload(draftId, TOURNAMENT_ID, 1, "2026-07-24", "Bermuda", "draft", 1),
            MatchCloudRestorePayload(finalId, TOURNAMENT_ID, 2, "2026-07-24", "Purgatory", "finalized", 1),
        )
        val tournamentUuid = UUID.fromString(TOURNAMENT_ID)
        val results = listOf(MatchResultCloudRestorePayload(UUID.randomUUID().toString(), draftId, TournamentCloudIdentity.teamSlotId(tournamentUuid, 1), null, 2)) +
            (1..12).map { slot -> MatchResultCloudRestorePayload(UUID.randomUUID().toString(), finalId, TournamentCloudIdentity.teamSlotId(tournamentUuid, slot), slot, slot - 1) }
        return MatchCloudRestorationPayloads(TOURNAMENT_ID, matches, results, cloudRevision = 1)
    }
    private companion object { const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111" }
}
