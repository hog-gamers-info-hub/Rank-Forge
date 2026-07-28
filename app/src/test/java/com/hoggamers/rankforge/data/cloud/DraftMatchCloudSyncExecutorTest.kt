package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftMatchCloudSyncExecutorTest {
    @Test
    fun uploadsMatchesBeforeResults() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = DraftMatchCloudSyncExecutor(
            upsertMatches = { calls += "matches" },
            upsertMatchResults = { calls += "match_results" },
        )

        val result = executor.execute(payloads())

        assertEquals(DraftMatchCloudSyncExecutionResult.Success, result)
        assertEquals(listOf("matches", "match_results"), calls)
    }

    @Test
    fun reportsPartialFailureAfterMatchesWithoutRollback() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = DraftMatchCloudSyncExecutor(
            upsertMatches = { calls += "matches" },
            upsertMatchResults = { calls += "match_results"; error("network unavailable") },
        )

        val result = executor.execute(payloads()) as DraftMatchCloudSyncExecutionResult.Failure

        assertEquals(DraftMatchCloudSyncCompletedStage.MATCHES, result.completedStage)
        assertEquals(DraftMatchCloudSyncFailureCategory.NETWORK, result.category)
        assertEquals(listOf("matches", "match_results"), calls)
        assertTrue("rollback" !in calls)
    }

    private fun payloads() = DraftMatchCloudSyncPayloads(
        matches = listOf(
            DraftMatchUploadPayload(
                id = "11111111-1111-1111-1111-111111111111",
                tournamentId = "22222222-2222-2222-2222-222222222222",
                matchNumber = 1,
                matchDate = "2026-07-24",
                mapName = "Bermuda",
                status = "draft",
            ),
        ),
        matchResults = listOf(
            DraftMatchResultUploadPayload(
                id = "33333333-3333-3333-3333-333333333333",
                matchId = "11111111-1111-1111-1111-111111111111",
                teamSlotId = "44444444-4444-4444-4444-444444444444",
                placement = null,
                kills = 0,
                source = "manual",
                reviewStatus = "draft",
            ),
        ),
    )
}
