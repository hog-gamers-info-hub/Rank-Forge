package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchOcrEvidenceCloudDataSourceTest {
    @Test
    fun repeatedUpsertReplacesChildrenIdempotentlyAndReadRestoresAllEvidence() = runBlocking {
        val evidenceByMatch = linkedMapOf<String, MatchOcrEvidenceCloudPayload>()
        val rowsByMatch = linkedMapOf<String, MutableList<FinalizedMatchOcrEvidenceRowUploadPayload>>()
        val correctionsByMatch = linkedMapOf<String, MutableList<FinalizedMatchOcrCorrectionSnapshotUploadPayload>>()
        val dataSource = SupabaseMatchOcrEvidenceCloudDataSource(
            isConfigured = { true },
            currentUserId = { OWNER_ID },
            upsertEvidence = { payloads -> payloads.forEach { evidenceByMatch[it.matchId] = it } },
            deleteRows = { matchId -> rowsByMatch.remove(matchId) },
            deleteCorrectionSnapshots = { matchId -> correctionsByMatch.remove(matchId) },
            insertRows = { payloads ->
                payloads.groupBy { it.matchId }.forEach { (matchId, values) ->
                    rowsByMatch.getOrPut(matchId) { mutableListOf() }.addAll(values)
                }
            },
            insertCorrectionSnapshots = { payloads ->
                payloads.groupBy { it.matchId }.forEach { (matchId, values) ->
                    correctionsByMatch.getOrPut(matchId) { mutableListOf() }.addAll(values)
                }
            },
            readEvidence = { tournamentId -> evidenceByMatch.values.filter { it.tournamentId == tournamentId } },
            readRows = { matchId -> rowsByMatch[matchId].orEmpty() },
            readCorrectionSnapshots = { matchId -> correctionsByMatch[matchId].orEmpty() },
        )
        val payload = FinalizedMatchOcrEvidenceUploadPayload(
            matchId = MATCH_ID,
            tournamentId = TOURNAMENT_ID,
            sourceScreenshotId = "MATCH_RESULT_UPPER",
            preservedAt = "2026-08-15T12:34:56Z",
            provenance = "OCR_REVIEW_FINALIZATION",
            rows = listOf(
                FinalizedMatchOcrEvidenceRowUploadPayload(
                    matchId = MATCH_ID,
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
                    matchId = MATCH_ID,
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
        )

        assertEquals(MatchOcrEvidenceCloudResult.Success, dataSource.upsert(listOf(payload)))
        assertEquals(MatchOcrEvidenceCloudResult.Success, dataSource.upsert(listOf(payload)))
        assertEquals(1, evidenceByMatch.size)
        assertEquals(1, rowsByMatch[MATCH_ID]?.size)
        assertEquals(1, correctionsByMatch[MATCH_ID]?.size)

        val restored = dataSource.readByTournamentAndMatchIds(TOURNAMENT_ID, setOf(MATCH_ID))
            as MatchOcrEvidenceCloudReadResult.Success
        assertEquals(
            payload.copy(rows = emptyList(), correctionSnapshots = emptyList()),
            restored.snapshots.single().evidence.toUploadPayload(),
        )
        assertEquals(payload.rows, restored.snapshots.single().rows)
        assertEquals(payload.correctionSnapshots, restored.snapshots.single().correctionSnapshots)
    }

    @Test
    fun cancellationDuringEvidenceUploadPropagates() {
        val cancellation = CancellationException("cancelled")
        val dataSource = SupabaseMatchOcrEvidenceCloudDataSource(
            isConfigured = { true },
            currentUserId = { OWNER_ID },
            upsertEvidence = { throw cancellation },
            deleteRows = {},
            deleteCorrectionSnapshots = {},
            insertRows = {},
            insertCorrectionSnapshots = {},
            readEvidence = { emptyList() },
            readRows = { emptyList() },
            readCorrectionSnapshots = { emptyList() },
        )

        assertThrows(CancellationException::class.java) {
            runBlocking {
                dataSource.upsert(
                    listOf(
                        FinalizedMatchOcrEvidenceUploadPayload(
                            matchId = MATCH_ID,
                            tournamentId = TOURNAMENT_ID,
                            sourceScreenshotId = null,
                            preservedAt = "2026-08-15T12:34:56Z",
                            provenance = "OCR_REVIEW_FINALIZATION",
                        ),
                    ),
                )
            }
        }
    }

    private fun MatchOcrEvidenceCloudPayload.toUploadPayload() = FinalizedMatchOcrEvidenceUploadPayload(
        matchId = matchId,
        tournamentId = tournamentId,
        sourceScreenshotId = sourceScreenshotId,
        preservedAt = preservedAt,
        provenance = provenance,
    )

    private companion object {
        const val OWNER_ID = "owner-id"
        const val TOURNAMENT_ID = "tournament-id"
        const val MATCH_ID = "match-id"
    }
}
