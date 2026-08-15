package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchStatus
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

    @Test
    fun restoresHistoricalOcrEvidenceWithRowsAndCorrectionsWithoutChangingMatchIdentity() {
        val payload = validPayloads()
        val finalizedId = payload.matches.last().id
        val mapped = MatchCloudRestorationMapper.map(
            payload.copy(
                ocrEvidence = listOf(
                    MatchOcrEvidenceCloudSnapshot(
                        evidence = MatchOcrEvidenceCloudPayload(
                            matchId = finalizedId,
                            tournamentId = TOURNAMENT_ID,
                            sourceScreenshotId = "MATCH_RESULT_UPPER",
                            preservedAt = "2026-08-15T12:34:56Z",
                            provenance = "OCR_REVIEW_FINALIZATION",
                        ),
                        rows = listOf(
                            FinalizedMatchOcrEvidenceRowUploadPayload(
                                matchId = finalizedId,
                                tournamentId = TOURNAMENT_ID,
                                rowIndex = 1,
                                originalOcrText = "Alpha",
                                originalPlacement = 2,
                                originalKills = 4,
                                originalSuggestedTeamSlot = 3,
                                confidenceSummary = "HIGH",
                                safetySummary = "SAFE",
                                manualReviewRequired = true,
                            ),
                        ),
                        correctionSnapshots = listOf(
                            FinalizedMatchOcrCorrectionSnapshotUploadPayload(
                                matchId = finalizedId,
                                tournamentId = TOURNAMENT_ID,
                                rowIndex = 1,
                                correctedPlacement = 2,
                                correctedKills = 4,
                                correctedTeamSlot = 3,
                                placementChanged = true,
                                killsChanged = false,
                                teamSlotChanged = true,
                                preservedAt = "2026-08-15T12:34:56Z",
                                provenance = "OCR_REVIEW_FINALIZATION",
                            ),
                        ),
                    ),
                ),
            ),
        ) as MatchCloudRestorationMappingResult.Success

        val evidence = mapped.value.ocrEvidence.single()
        assertEquals(finalizedId, mapped.value.matches.last().id)
        assertEquals(finalizedId, evidence.matchId)
        assertEquals(TOURNAMENT_ID, evidence.tournamentId)
        assertEquals("MATCH_RESULT_UPPER", evidence.sourceScreenshotId)
        assertEquals("OCR_REVIEW_FINALIZATION", evidence.provenance)
        assertEquals(1, evidence.rows.single().rowIndex)
        assertEquals("Alpha", evidence.rows.single().originalOcrText)
        assertEquals(2, evidence.correctionSnapshots.single().correctedPlacement)
        assertEquals(true, evidence.correctionSnapshots.single().placementChanged)
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

    @Test fun rejectsIncompleteOrDuplicateFinalizedPlacements() {
        val payload = validPayloads()
        val finalizedId = payload.matches.last().id
        val incomplete = payload.copy(results = payload.results.filterNot { it.matchId == finalizedId && it.placement == 12 })
        val duplicate = payload.copy(results = payload.results.map { if (it.matchId == finalizedId && it.placement == 12) it.copy(placement = 1) else it })
        assertEquals(MatchCloudRestorationMappingResult.Invalid, MatchCloudRestorationMapper.map(incomplete))
        assertEquals(MatchCloudRestorationMappingResult.Invalid, MatchCloudRestorationMapper.map(duplicate))
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
